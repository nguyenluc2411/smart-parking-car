import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/widgets/state_views.dart';
import '../data/driver_models.dart';

/// Bản đồ THẬT của bãi (BR-009-10, GET /driver/slots) để tài xế chọn zone + ô
/// trước khi đặt chỗ. Trả về [DriverSlot] đã chọn qua `Navigator.pop`, hoặc
/// `null` nếu tài xế muốn để hệ thống tự chọn.
final driverSlotsProvider =
    FutureProvider.autoDispose<List<DriverSlot>>((ref) {
  return ref.watch(driverRepositoryProvider).listSlots();
});

class SlotPickerScreen extends ConsumerWidget {
  const SlotPickerScreen({super.key});

  static const _cols = 10; // khớp quy ước seed (10 ô/hàng/zone)

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(driverSlotsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chọn chỗ đỗ'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, null),
            child: const Text('Để hệ thống tự chọn'),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(driverSlotsProvider),
        child: AsyncView<List<DriverSlot>>(
          value: async,
          onRetry: () => ref.invalidate(driverSlotsProvider),
          data: (slots) {
            if (slots.isEmpty) {
              return const Center(child: Text('Bãi chưa có slot nào'));
            }
            final byZone = <String, List<DriverSlot>>{};
            for (final s in slots) {
              byZone.putIfAbsent(s.zone, () => []).add(s);
            }
            final zones = byZone.keys.toList()..sort();
            return ListView(
              padding: const EdgeInsets.all(12),
              children: [
                _Legend(),
                const SizedBox(height: 12),
                for (final zone in zones) ...[
                  _ZoneSection(zone: zone, slots: byZone[zone]!, cols: _cols),
                  const SizedBox(height: 20),
                ],
              ],
            );
          },
        ),
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget dot(Color c, String label) => Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
                width: 14,
                height: 14,
                decoration: BoxDecoration(
                    color: c, borderRadius: BorderRadius.circular(3))),
            const SizedBox(width: 4),
            Text(label, style: theme.textTheme.bodySmall),
          ],
        );
    return Wrap(
      spacing: 16,
      runSpacing: 4,
      children: [
        dot(statusColor(context, 'EMPTY'), 'Trống'),
        dot(statusColor(context, 'OCCUPIED'), 'Đã có xe'),
        dot(statusColor(context, 'RESERVED'), 'Đã được đặt'),
        dot(statusColor(context, 'MAINTENANCE'), 'Bảo trì'),
      ],
    );
  }
}

class _ZoneSection extends StatelessWidget {
  const _ZoneSection({required this.zone, required this.slots, required this.cols});

  final String zone;
  final List<DriverSlot> slots;
  final int cols;

  @override
  Widget build(BuildContext context) {
    final placed = slots.where((s) => s.gridRow != null && s.gridCol != null).toList();
    final unplaced = slots.where((s) => s.gridRow == null || s.gridCol == null).toList();
    final emptyCount = slots.where((s) => s.status == 'EMPTY').length;

    final rows = placed.isEmpty
        ? 0
        : placed.map((s) => s.gridRow!).reduce((a, b) => a > b ? a : b) + 1;

    final grid = <String?, DriverSlot>{}; // "row,col" -> slot
    for (final s in placed) {
      grid['${s.gridRow}-${s.gridCol}'] = s;
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Zone $zone · $emptyCount/${slots.length} trống',
                style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            if (rows > 0)
              AspectRatio(
                aspectRatio: cols / rows,
                child: GridView.builder(
                  physics: const NeverScrollableScrollPhysics(),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: cols,
                    crossAxisSpacing: 3,
                    mainAxisSpacing: 3,
                  ),
                  itemCount: cols * rows,
                  itemBuilder: (context, i) {
                    final r = i ~/ cols;
                    final c = i % cols;
                    final slot = grid['$r-$c'];
                    return _SlotCell(slot: slot);
                  },
                ),
              ),
            if (unplaced.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('Chưa có tọa độ trên bản đồ:',
                  style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 4),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: unplaced
                    .map((s) => ActionChip(
                          label: Text(s.slotCode),
                          backgroundColor:
                              statusColor(context, s.status).withValues(alpha: 0.15),
                          onPressed: s.status == 'EMPTY'
                              ? () => Navigator.pop(context, s)
                              : null,
                        ))
                    .toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _SlotCell extends StatelessWidget {
  const _SlotCell({required this.slot});

  final DriverSlot? slot;

  @override
  Widget build(BuildContext context) {
    if (slot == null) {
      return const SizedBox.shrink();
    }
    final theme = Theme.of(context);
    final selectable = slot!.status == 'EMPTY';
    return Tooltip(
      message: '${slot!.slotCode} · ${_statusLabel(slot!.status)}',
      child: InkWell(
        borderRadius: BorderRadius.circular(4),
        onTap: selectable ? () => Navigator.pop(context, slot) : null,
        child: Container(
          decoration: BoxDecoration(
            color: statusColor(context, slot!.status)
                .withValues(alpha: selectable ? 1 : 0.55),
            borderRadius: BorderRadius.circular(4),
            border: Border.all(color: theme.colorScheme.outlineVariant, width: 0.5),
          ),
          alignment: Alignment.center,
          child: Text(
            slot!.slotCode,
            style: TextStyle(
              fontSize: 8,
              color: selectable
                  ? theme.colorScheme.onPrimary
                  : theme.colorScheme.surface,
            ),
            overflow: TextOverflow.clip,
            maxLines: 1,
          ),
        ),
      ),
    );
  }

  String _statusLabel(String s) => switch (s) {
        'EMPTY' => 'Trống — chạm để chọn',
        'OCCUPIED' => 'Đã có xe',
        'RESERVED' => 'Đã được đặt',
        'MAINTENANCE' => 'Bảo trì',
        _ => s,
      };
}
