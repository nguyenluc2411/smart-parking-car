import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/providers.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

final sessionDetailProvider =
    FutureProvider.family<SessionDetail, String>(
  (ref, id) => ref.watch(parkingRepositoryProvider).getSession(id),
);

class SessionDetailScreen extends ConsumerWidget {
  const SessionDetailScreen({super.key, required this.sessionId});
  final String sessionId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(sessionDetailProvider(sessionId));
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chi tiết phiên'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () =>
              context.canPop() ? context.pop() : context.go('/sessions'),
        ),
      ),
      body: AsyncView<SessionDetail>(
        value: detail,
        onRetry: () => ref.invalidate(sessionDetailProvider(sessionId)),
        data: (s) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(s.plateNumber,
                            style: Theme.of(context)
                                .textTheme
                                .headlineSmall
                                ?.copyWith(fontWeight: FontWeight.bold)),
                        StatusChip(
                            label: Fmt.sessionStatus(s.status),
                            color: statusColor(context, s.status)),
                      ],
                    ),
                    const Divider(height: 32),
                    _kv(context, 'Vị trí',
                        s.slot == null ? '—' : '${s.slot!.slotCode} (Khu ${s.slot!.zone})'),
                    _kv(context, 'Cổng vào', s.entryGate?.gateCode ?? '—'),
                    _kv(context, 'Cổng ra', s.exitGate?.gateCode ?? '—'),
                    _kv(context, 'Giờ vào', Fmt.dateTime(s.entryTime)),
                    _kv(context, 'Giờ ra', Fmt.dateTime(s.exitTime)),
                    _kv(context, 'Thời lượng',
                        Fmt.duration(s.durationSeconds)),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Ảnh xe vào / ra',
                        style: Theme.of(context)
                            .textTheme
                            .titleMedium
                            ?.copyWith(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 12),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                            child: _snapshot(context, 'Vào', s.entryImageUrl)),
                        const SizedBox(width: 12),
                        Expanded(
                            child: _snapshot(context, 'Ra', s.exitImageUrl)),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () => context.go('/billing?sessionId=${s.id}'),
              icon: const Icon(Icons.receipt_long),
              label: const Text('Xem hóa đơn / Thanh toán'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _snapshot(BuildContext context, String label, String? url) {
    final outline = Theme.of(context).colorScheme.outline;
    Widget placeholder(String text) => AspectRatio(
          aspectRatio: 4 / 3,
          child: Container(
            decoration: BoxDecoration(
              border: Border.all(color: outline),
              borderRadius: BorderRadius.circular(8),
            ),
            alignment: Alignment.center,
            child: Text(text, style: TextStyle(color: outline)),
          ),
        );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: TextStyle(color: outline)),
        const SizedBox(height: 6),
        if (url == null)
          placeholder('Không có ảnh')
        else
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            // Presigned URL tự xác thực — load trực tiếp, không cần header Authorization.
            child: AspectRatio(
              aspectRatio: 4 / 3,
              child: Image.network(
                url,
                fit: BoxFit.cover,
                loadingBuilder: (c, child, progress) => progress == null
                    ? child
                    : const Center(child: CircularProgressIndicator()),
                errorBuilder: (c, e, st) => placeholder('Lỗi tải ảnh'),
              ),
            ),
          ),
      ],
    );
  }

  Widget _kv(BuildContext context, String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 110,
              child: Text(k,
                  style:
                      TextStyle(color: Theme.of(context).colorScheme.outline)),
            ),
            Expanded(
              child: Text(v,
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
          ],
        ),
      );
}
