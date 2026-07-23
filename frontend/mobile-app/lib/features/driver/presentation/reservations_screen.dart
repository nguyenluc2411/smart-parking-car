import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show Clipboard, ClipboardData;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../billing/data/billing_models.dart' show ReservationFee;
import '../data/driver_models.dart';
import 'my_vehicles_screen.dart' show driverProfileProvider;
import 'slot_map_widget.dart';
import 'slot_picker_screen.dart';

/// Lượt đặt chỗ của tài xế (parking GET /driver/reservations, BR-009).
final driverReservationsProvider =
    FutureProvider.autoDispose<PageResult<Reservation>>((ref) {
  return ref.watch(driverRepositoryProvider).myReservations();
});

class ReservationsScreen extends ConsumerWidget {
  const ReservationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(driverReservationsProvider);
    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(driverReservationsProvider),
        child: AsyncView<PageResult<Reservation>>(
          value: async,
          onRetry: () => ref.invalidate(driverReservationsProvider),
          data: (page) {
            if (page.content.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 120),
                  EmptyView(
                      message: 'Chưa có lượt đặt chỗ nào',
                      icon: Icons.event_seat_outlined),
                ],
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
              itemCount: page.content.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (_, i) =>
                  _ReservationCard(reservation: page.content[i]),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _createReservation(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Đặt chỗ'),
      ),
    );
  }

  Future<void> _createReservation(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);

    // Biển đã khai — cần được duyệt (verified) mới đặt chỗ được (BR-009-1).
    final profile = await ref.read(driverProfileProvider.future);
    final verifiedPlates =
        profile.vehicles.where((v) => v.verified).toList(growable: false);
    if (verifiedPlates.isEmpty) {
      messenger.showSnackBar(const SnackBar(
          content: Text(
              'Chưa có biển số nào được duyệt — khai biển ở tab "Biển số" trước.')));
      return;
    }

    if (!context.mounted) return;
    final result = await showModalBottomSheet<_BookingInput>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => _BookingSheet(plates: verifiedPlates),
    );
    if (result == null) return;

    try {
      final reservation = await ref.read(driverRepositoryProvider).createReservation(
            plateNumber: result.plateNumber,
            startTime: result.startTime,
            slotId: result.slot?.id,
          );
      ref.invalidate(driverReservationsProvider);

      // BR-009-11: phí đặt chỗ — không chặn nếu tạo phí lỗi, giữ chỗ vẫn có hiệu lực,
      // tài xế có thể mở lại và trả sau (chưa hỗ trợ retry riêng trong bản này).
      ReservationFee? fee;
      try {
        fee = await ref.read(driverRepositoryProvider).createReservationFee(
              reservationId: reservation.id,
              plateNumber: reservation.plateNumber,
              reservationStartTime: result.startTime,
            );
      } catch (_) {
        fee = null;
      }

      if (!context.mounted) return;
      await showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Đã giữ chỗ'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Ô ${reservation.slotCode} · Zone ${reservation.zone}'),
                Text('Giữ đến: ${Fmt.dateTime(reservation.holdUntil)}'),
                const SizedBox(height: 12),
                SlotMapWidget(
                  zone: reservation.zone,
                  slotCode: reservation.slotCode,
                  gridRow: reservation.gridRow,
                  gridCol: reservation.gridCol,
                ),
                const SizedBox(height: 12),
                if (fee != null && fee.checkoutUrl != null)
                  _FeePaymentBox(reservationId: reservation.id, fee: fee)
                else
                  Text(
                    'Chưa tạo được phí đặt chỗ — bạn vẫn giữ chỗ bình thường, '
                    'mở lại mục này sau để thử thanh toán.',
                    style: Theme.of(ctx).textTheme.bodySmall
                        ?.copyWith(color: Theme.of(ctx).colorScheme.outline),
                  ),
              ],
            ),
          ),
          actions: [
            FilledButton(
                onPressed: () => Navigator.pop(ctx), child: const Text('OK')),
          ],
        ),
      );
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Lỗi: ${e.message}')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Đặt chỗ thất bại, thử lại sau.')));
    }
  }
}

/// Hiển thị link/QR PayOS cho phí đặt chỗ (BR-009-11) + nút kiểm tra lại trạng thái sau khi
/// tài xế đã thanh toán trên trang PayOS (thanh toán xử lý qua webhook, không đồng bộ ngay).
class _FeePaymentBox extends ConsumerStatefulWidget {
  const _FeePaymentBox({required this.reservationId, required this.fee});
  final String reservationId;
  final ReservationFee fee;

  @override
  ConsumerState<_FeePaymentBox> createState() => _FeePaymentBoxState();
}

class _FeePaymentBoxState extends ConsumerState<_FeePaymentBox> {
  late ReservationFee _fee = widget.fee;
  bool _checking = false;

  Future<void> _check() async {
    setState(() => _checking = true);
    try {
      final f =
          await ref.read(driverRepositoryProvider).getReservationFee(widget.reservationId);
      if (mounted) setState(() => _fee = f);
    } catch (_) {
      // giữ trạng thái cũ, tài xế bấm thử lại sau
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_fee.status == 'PAID') {
      return Row(
        children: [
          Icon(Icons.check_circle, color: Colors.green.shade700, size: 18),
          const SizedBox(width: 6),
          Text('Đã thanh toán phí đặt chỗ (${Fmt.currency(_fee.amount)})'),
        ],
      );
    }
    return Card(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Phí đặt chỗ: ${Fmt.currency(_fee.amount)}',
                style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            if (_fee.checkoutUrl != null)
              Row(
                children: [
                  Expanded(
                    child: SelectableText(_fee.checkoutUrl!,
                        style: Theme.of(context).textTheme.bodySmall,
                        maxLines: 1),
                  ),
                  IconButton(
                    icon: const Icon(Icons.copy, size: 18),
                    tooltip: 'Sao chép link thanh toán',
                    onPressed: () => Clipboard.setData(
                        ClipboardData(text: _fee.checkoutUrl!)),
                  ),
                ],
              ),
            const SizedBox(height: 4),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: _checking ? null : _check,
                icon: _checking
                    ? const SizedBox(
                        width: 14, height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.refresh),
                label: const Text('Tôi đã thanh toán — Kiểm tra'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ReservationCard extends ConsumerWidget {
  const _ReservationCard({required this.reservation});

  final Reservation reservation;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final canCancel = reservation.status == 'HELD';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${reservation.plateNumber} · ô ${reservation.slotCode}',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                StatusChip(
                  label: Fmt.reservationStatus(reservation.status),
                  color: statusColor(context, reservation.status),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text('Giờ vào dự kiến: ${Fmt.dateTime(reservation.startTime)}'),
            Text('Giữ đến: ${Fmt.dateTime(reservation.holdUntil)}'),
            const SizedBox(height: 8),
            Row(
              children: [
                TextButton.icon(
                  onPressed: () => _showMap(context),
                  icon: const Icon(Icons.map_outlined),
                  label: const Text('Xem sơ đồ'),
                ),
                const Spacer(),
                if (canCancel)
                  OutlinedButton.icon(
                    onPressed: () => _cancel(context, ref),
                    icon: const Icon(Icons.close),
                    label: const Text('Hủy'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showMap(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        content: SlotMapWidget(
          zone: reservation.zone,
          slotCode: reservation.slotCode,
          gridRow: reservation.gridRow,
          gridCol: reservation.gridCol,
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Đóng')),
        ],
      ),
    );
  }

  Future<void> _cancel(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Hủy lượt đặt chỗ'),
        content: Text(
            'Hủy giữ chỗ ô ${reservation.slotCode} cho ${reservation.plateNumber}?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Không')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Hủy chỗ')),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      await ref.read(driverRepositoryProvider).cancelReservation(reservation.id);
      ref.invalidate(driverReservationsProvider);

      // BR-009-11: quyết toán phí (hoàn/mất) — best-effort, không chặn việc hủy đã thành công
      // dù bước này lỗi (vd chưa từng tạo phí cho lượt đặt này).
      String feeMsg = '';
      try {
        final fee =
            await ref.read(driverRepositoryProvider).refundReservationFee(reservation.id);
        feeMsg = fee.status == 'REFUNDED'
            ? ' — đã hoàn ${Fmt.currency(fee.amount)}'
            : fee.status == 'FORFEITED'
                ? ' — hủy sát giờ nên mất phí ${Fmt.currency(fee.amount)}'
                : '';
      } catch (_) {
        // không có phí nào để quyết toán, hoặc gọi lỗi — bỏ qua, việc hủy vẫn thành công
      }
      messenger.showSnackBar(
          SnackBar(content: Text('Đã hủy lượt đặt chỗ$feeMsg')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Lỗi: ${e.message}')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Hủy thất bại, thử lại sau.')));
    }
  }
}

class _BookingInput {
  const _BookingInput(
      {required this.plateNumber, required this.startTime, this.slot});
  final String plateNumber;
  final DateTime startTime;
  /// Null = để hệ thống tự chọn slot (hành vi mặc định trước đây).
  final DriverSlot? slot;
}

class _BookingSheet extends StatefulWidget {
  const _BookingSheet({required this.plates});
  final List<DriverVehicle> plates;

  @override
  State<_BookingSheet> createState() => _BookingSheetState();
}

class _BookingSheetState extends State<_BookingSheet> {
  late String _plate = widget.plates.first.plateNumber;
  DateTime _startTime = DateTime.now().add(const Duration(minutes: 30));
  DriverSlot? _slot;

  // max-lead-hours mặc định server-side là 72h (BR-009) — giới hạn UI khớp để
  // tránh chọn giờ chắc chắn bị từ chối; server vẫn là nguồn sự thật cuối cùng.
  static const _maxLeadHours = 72;

  Future<void> _pickSlot() async {
    final chosen = await Navigator.of(context).push<DriverSlot?>(
      MaterialPageRoute(builder: (_) => const SlotPickerScreen()),
    );
    if (!mounted) return;
    // `chosen == null` bao gồm cả "bấm back" lẫn "để hệ thống tự chọn" — cả
    // hai đều nghĩa là không tự chỉ định slot, nên gán null là đúng trong cả hai.
    setState(() => _slot = chosen);
  }

  Future<void> _pickDateTime() async {
    final now = DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: _startTime,
      firstDate: now,
      lastDate: now.add(const Duration(hours: _maxLeadHours)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_startTime),
    );
    if (time == null) return;
    setState(() {
      _startTime =
          DateTime(date.year, date.month, date.day, time.hour, time.minute);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Đặt chỗ mới', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _plate,
            decoration: const InputDecoration(labelText: 'Biển số'),
            items: widget.plates
                .map((v) => DropdownMenuItem(
                      value: v.plateNumber,
                      child: Text(v.plateNumber),
                    ))
                .toList(),
            onChanged: (v) => setState(() => _plate = v ?? _plate),
          ),
          const SizedBox(height: 16),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.schedule),
            title: const Text('Giờ vào dự kiến'),
            subtitle: Text(Fmt.dateTime(_startTime.toIso8601String())),
            onTap: _pickDateTime,
            trailing: const Icon(Icons.edit_outlined),
          ),
          const SizedBox(height: 16),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.map_outlined),
            title: const Text('Chỗ đỗ'),
            subtitle: Text(_slot == null
                ? 'Để hệ thống tự chọn'
                : '${_slot!.slotCode} · Zone ${_slot!.zone}'),
            onTap: _pickSlot,
            trailing: const Icon(Icons.chevron_right),
          ),
          const SizedBox(height: 8),
          Text(
            'Chỗ sẽ được giữ trong một khoảng thời gian ngắn kể từ giờ vào dự '
            'kiến — không tới đúng hẹn sẽ tính là một lần bỏ hẹn.',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.outline),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => Navigator.pop(
              context,
              _BookingInput(
                  plateNumber: _plate, startTime: _startTime, slot: _slot),
            ),
            child: const Text('Xác nhận đặt chỗ'),
          ),
        ],
      ),
    );
  }
}
