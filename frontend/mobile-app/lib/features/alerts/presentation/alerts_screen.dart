import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

/// Open (NEW) alerts worklist.
final alertsProvider = FutureProvider.autoDispose<List<Alert>>(
  (ref) => ref.watch(parkingRepositoryProvider).getAlerts(status: 'NEW'),
);

/// Live SSE stream of alerts — kept open while anything watches it (the shell + this screen).
final alertStreamProvider = StreamProvider.autoDispose<Alert>(
  (ref) => ref.watch(parkingRepositoryProvider).streamAlerts(),
);

const _typeLabel = <String, String>{
  'DUPLICATE_ACTIVE_ENTRY': 'Nghi biển giả / clone',
  'BLACKLIST_HIT': 'Xe trong danh sách cấm',
  'UNMATCHED_EXIT': 'Xe ra không khớp phiên',
  'LOW_CONFIDENCE': 'Đọc biển không chắc chắn',
};

/// Câu mô tả gọn, soạn ở client (không dùng message thô của backend) cho nhất quán/dễ đọc.
String _describe(Alert a) {
  final plate = a.plateNumber ?? 'không rõ';
  final at = a.gateId != null ? ' tại ${a.gateId}' : '';
  switch (a.alertType) {
    case 'DUPLICATE_ACTIVE_ENTRY':
      return 'Biển $plate đã có xe trong bãi nhưng lại được quét vào$at.';
    case 'BLACKLIST_HIT':
      return 'Biển $plate thuộc danh sách cấm, bị từ chối vào$at.';
    case 'UNMATCHED_EXIT':
      return 'Biển $plate ra bãi$at nhưng không có phiên đang mở.';
    case 'LOW_CONFIDENCE':
      return 'Đọc biển $plate$at dưới ngưỡng tin cậy.';
    default:
      return a.message;
  }
}

/// Bấm cảnh báo → tới đúng trang xử lý nhanh nhất. Blacklist bị từ chối vào (không có phiên)
/// nên dẫn tới tab danh sách cấm; có phiên thì mở chi tiết phiên; còn lại lọc theo biển.
String? _routeFor(Alert a) {
  if (a.alertType == 'BLACKLIST_HIT') return '/vehicles?tab=blacklist';
  if (a.sessionId != null) return '/sessions/${a.sessionId}';
  if (a.plateNumber != null) {
    return '/sessions?plate=${Uri.encodeComponent(a.plateNumber!)}';
  }
  return null;
}

class AlertsScreen extends ConsumerWidget {
  const AlertsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Live push: refresh the list whenever a new alert arrives over SSE.
    ref.listen(alertStreamProvider, (_, next) {
      next.whenData((_) => ref.invalidate(alertsProvider));
    });

    final alerts = ref.watch(alertsProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(alertsProvider),
      child: AsyncView<List<Alert>>(
        value: alerts,
        onRetry: () => ref.invalidate(alertsProvider),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(message: 'Không có cảnh báo');
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: list.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _AlertTile(alert: list[i]),
          );
        },
      ),
    );
  }
}

class _AlertTile extends ConsumerStatefulWidget {
  const _AlertTile({required this.alert});
  final Alert alert;

  @override
  ConsumerState<_AlertTile> createState() => _AlertTileState();
}

class _AlertTileState extends ConsumerState<_AlertTile> {
  bool _loading = false;

  Future<void> _ack() async {
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _loading = true);
    try {
      await ref.read(parkingRepositoryProvider).ackAlert(widget.alert.id);
      ref.invalidate(alertsProvider);
      messenger.showSnackBar(const SnackBar(content: Text('Đã xử lý cảnh báo')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.alert;
    final color = a.isCritical ? Colors.red : Colors.orange;
    final route = _routeFor(a);
    return Card(
      child: ListTile(
        onTap: route == null ? null : () => context.go(route),
        leading: CircleAvatar(
          backgroundColor: color.withValues(alpha: 0.15),
          child: Icon(a.isCritical ? Icons.gpp_bad : Icons.warning_amber,
              color: color),
        ),
        title: Text(
          '${_typeLabel[a.alertType] ?? a.alertType}'
          '${a.plateNumber != null ? ' · ${a.plateNumber}' : ''}',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Text('${_describe(a)}\n${Fmt.date(a.createdAt)}'),
        isThreeLine: true,
        trailing: _loading
            ? const SizedBox(
                height: 22,
                width: 22,
                child: CircularProgressIndicator(strokeWidth: 2.5))
            : IconButton(
                icon: const Icon(Icons.check_circle_outline),
                tooltip: 'Đã xử lý',
                onPressed: _ack,
              ),
      ),
    );
  }
}
