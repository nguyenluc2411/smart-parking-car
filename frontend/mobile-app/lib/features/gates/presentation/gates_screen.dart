import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/barrier_view.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

/// Poll mỗi 3s để barie minh họa cập nhật gần real-time (chưa có WebSocket/camera).
final gatesProvider = StreamProvider.autoDispose<List<Gate>>((ref) async* {
  final repo = ref.watch(parkingRepositoryProvider);
  while (true) {
    yield await repo.getGates();
    await Future<void>.delayed(const Duration(seconds: 3));
  }
});

class GatesScreen extends ConsumerWidget {
  const GatesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final gates = ref.watch(gatesProvider);

    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(gatesProvider),
      child: AsyncView<List<Gate>>(
        value: gates,
        onRetry: () => ref.invalidate(gatesProvider),
        // OPERATOR & ADMIN đều được đóng/mở barie (backend: /gates/*/override).
        data: (list) => ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: list.length,
          separatorBuilder: (_, _) => const SizedBox(height: 12),
          itemBuilder: (_, i) =>
              _GateCard(gate: list[i], canOverride: true),
        ),
      ),
    );
  }
}

class _GateCard extends ConsumerWidget {
  const _GateCard({required this.gate, required this.canOverride});
  final Gate gate;
  final bool canOverride;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  gate.direction == 'IN' ? Icons.login : Icons.logout,
                  color: theme.colorScheme.primary,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(gate.gateCode,
                      style: theme.textTheme.titleMedium
                          ?.copyWith(fontWeight: FontWeight.bold)),
                ),
                StatusChip(
                    label: Fmt.gateStatus(gate.status),
                    color: statusColor(context, gate.status)),
              ],
            ),
            const SizedBox(height: 12),
            // Minh họa barie đóng/mở (thay cho camera bãi xe thật).
            BarrierView(
              open: gate.status.toUpperCase() == 'OPEN',
              direction: gate.direction,
            ),
            const SizedBox(height: 8),
            Text(
              'Hướng: ${Fmt.direction(gate.direction)} · Lệnh cuối: '
              '${gate.lastCommand == null ? '—' : Fmt.gateCommand(gate.lastCommand)} '
              '(${Fmt.dateTime(gate.lastCommandAt)})',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.outline),
            ),
            if (canOverride) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () =>
                          _override(context, ref, 'OPEN'),
                      icon: const Icon(Icons.lock_open, size: 18),
                      label: const Text('Mở'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () =>
                          _override(context, ref, 'CLOSE'),
                      icon: const Icon(Icons.lock_outline, size: 18),
                      label: const Text('Đóng'),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _override(
      BuildContext context, WidgetRef ref, String command) async {
    final messenger = ScaffoldMessenger.of(context);
    final reason = await _askReason(context, command);
    if (reason == null) return;
    try {
      await ref.read(parkingRepositoryProvider).overrideGate(
            id: gate.id,
            command: command,
            reason: reason,
          );
      ref.invalidate(gatesProvider);
      messenger.showSnackBar(
        SnackBar(
            content: Text(
                'Đã gửi lệnh ${Fmt.gateCommand(command)} tới ${gate.gateCode}')),
      );
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<String?> _askReason(BuildContext context, String command) {
    final ctrl = TextEditingController();
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Ghi đè lệnh ${Fmt.gateCommand(command)} · ${gate.gateCode}'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            labelText: 'Lý do',
            hintText: 'Ví dụ: xe bị kẹt, cần mở thủ công',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Hủy')),
          FilledButton(
            onPressed: () {
              if (ctrl.text.trim().isEmpty) return;
              Navigator.pop(ctx, ctrl.text.trim());
            },
            child: const Text('Xác nhận'),
          ),
        ],
      ),
    );
  }
}
