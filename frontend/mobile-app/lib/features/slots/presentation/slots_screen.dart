import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

final slotsProvider = FutureProvider<List<Slot>>(
  (ref) => ref.watch(parkingRepositoryProvider).getSlots(),
);

/// Quản lý bãi gửi xe (ADMIN): cấu hình nhanh theo khu + danh sách slot.
class SlotsScreen extends ConsumerWidget {
  const SlotsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      child: Column(
        children: [
          const Material(
            child: TabBar(
              tabs: [
                Tab(text: 'Cấu hình khu'),
                Tab(text: 'Danh sách slot'),
              ],
            ),
          ),
          const Expanded(
            child: TabBarView(
              children: [_ProvisionTab(), _SlotListTab()],
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Tab 1: cấu hình nhanh theo khu ──────────────────────────────────────────
class _ProvisionTab extends ConsumerStatefulWidget {
  const _ProvisionTab();
  @override
  ConsumerState<_ProvisionTab> createState() => _ProvisionTabState();
}

class _ProvisionTabState extends ConsumerState<_ProvisionTab> {
  final _zone = TextEditingController(text: 'A');
  final _count = TextEditingController(text: '10');
  bool _loading = false;

  @override
  void dispose() {
    _zone.dispose();
    _count.dispose();
    super.dispose();
  }

  Future<void> _apply() async {
    final messenger = ScaffoldMessenger.of(context);
    final zone = _zone.text.trim().toUpperCase();
    final count = int.tryParse(_count.text.trim());
    if (zone.isEmpty || count == null || count < 0) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Nhập khu và số slot hợp lệ')));
      return;
    }
    setState(() => _loading = true);
    try {
      final res = await ref
          .read(parkingRepositoryProvider)
          .provisionZone(zone: zone, count: count);
      ref.invalidate(slotsProvider);
      messenger.showSnackBar(SnackBar(
          content: Text('Khu ${res['zone']}: +${res['created']} / '
              '-${res['removed']} (tổng ${res['total']})')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final slots = ref.watch(slotsProvider);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Đặt một khu có đúng số slot mong muốn — tự sinh mã '
                  '${_zone.text.trim().isEmpty ? 'A' : _zone.text.trim().toUpperCase()}01… '
                  'Không xóa slot đang có xe.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    SizedBox(
                      width: 90,
                      child: TextField(
                        controller: _zone,
                        decoration: const InputDecoration(labelText: 'Khu'),
                        textCapitalization: TextCapitalization.characters,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        controller: _count,
                        keyboardType: TextInputType.number,
                        decoration:
                            const InputDecoration(labelText: 'Số slot'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: _loading ? null : _apply,
                  child: _loading
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2.5))
                      : const Text('Áp dụng'),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text('Các khu hiện có',
            style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        slots.when(
          loading: () =>
              const Center(child: Padding(
                  padding: EdgeInsets.all(16),
                  child: CircularProgressIndicator())),
          error: (e, _) => Text('$e'),
          data: (list) {
            final byZone = <String, int>{};
            for (final s in list) {
              byZone[s.zone] = (byZone[s.zone] ?? 0) + 1;
            }
            if (byZone.isEmpty) {
              return const Text('Chưa có slot nào.');
            }
            final zones = byZone.keys.toList()..sort();
            return Card(
              child: Column(
                children: [
                  for (final z in zones)
                    ListTile(
                      dense: true,
                      title: Text('Khu $z'),
                      trailing: Text('${byZone[z]} slot'),
                    ),
                ],
              ),
            );
          },
        ),
      ],
    );
  }
}

// ─── Tab 2: danh sách slot ───────────────────────────────────────────────────
class _SlotListTab extends ConsumerWidget {
  const _SlotListTab();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final slots = ref.watch(slotsProvider);
    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: () async => ref.invalidate(slotsProvider),
          child: AsyncView<List<Slot>>(
            value: slots,
            onRetry: () => ref.invalidate(slotsProvider),
            data: (list) {
              if (list.isEmpty) {
                return const EmptyView(message: 'Chưa có slot nào');
              }
              return ListView.separated(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 88),
                itemCount: list.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (_, i) => _SlotTile(slot: list[i]),
              );
            },
          ),
        ),
        Positioned(
          right: 16,
          bottom: 16,
          child: FloatingActionButton.extended(
            onPressed: () => _showAdd(context, ref),
            icon: const Icon(Icons.add),
            label: const Text('Thêm slot'),
          ),
        ),
      ],
    );
  }

  Future<void> _showAdd(BuildContext context, WidgetRef ref) async {
    final added = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const _AddSlotSheet(),
    );
    if (added == true) ref.invalidate(slotsProvider);
  }
}

class _SlotTile extends ConsumerWidget {
  const _SlotTile({required this.slot});
  final Slot slot;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final occupied = slot.status == 'OCCUPIED';
    final maintenance = slot.status == 'MAINTENANCE';
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor:
              statusColor(context, slot.status).withValues(alpha: 0.15),
          child: Icon(Icons.local_parking,
              color: statusColor(context, slot.status)),
        ),
        title: Text(slot.slotCode,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text('Khu ${slot.zone}'),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            StatusChip(
                label: FmtSlot.status(slot.status),
                color: statusColor(context, slot.status)),
            IconButton(
              tooltip: maintenance ? 'Mở lại' : 'Bảo trì',
              icon: Icon(maintenance ? Icons.restart_alt : Icons.build_outlined),
              onPressed: occupied
                  ? null
                  : () => _setStatus(
                      context, ref, maintenance ? 'EMPTY' : 'MAINTENANCE'),
            ),
            IconButton(
              tooltip: occupied ? 'Đang có xe' : 'Xóa',
              icon: const Icon(Icons.delete_outline),
              onPressed: occupied ? null : () => _delete(context, ref),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _setStatus(
      BuildContext context, WidgetRef ref, String status) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(parkingRepositoryProvider)
          .updateSlotStatus(id: slot.id, status: status);
      ref.invalidate(slotsProvider);
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa slot?'),
        content: Text(slot.slotCode),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Hủy')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Xóa')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await ref.read(parkingRepositoryProvider).deleteSlot(slot.id);
      ref.invalidate(slotsProvider);
      messenger.showSnackBar(const SnackBar(content: Text('Đã xóa')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }
}

class _AddSlotSheet extends ConsumerStatefulWidget {
  const _AddSlotSheet();
  @override
  ConsumerState<_AddSlotSheet> createState() => _AddSlotSheetState();
}

class _AddSlotSheetState extends ConsumerState<_AddSlotSheet> {
  final _formKey = GlobalKey<FormState>();
  final _code = TextEditingController();
  final _zone = TextEditingController(text: 'A');
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _code.dispose();
    _zone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(parkingRepositoryProvider).createSlot(
            slotCode: _code.text.trim().toUpperCase(),
            zone: _zone.text.trim().toUpperCase(),
          );
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Thêm slot', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            TextFormField(
              controller: _code,
              decoration: const InputDecoration(labelText: 'Mã slot *'),
              textCapitalization: TextCapitalization.characters,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Nhập mã slot' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _zone,
              decoration: const InputDecoration(labelText: 'Khu *'),
              textCapitalization: TextCapitalization.characters,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Nhập khu' : null,
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 22,
                      width: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Text('Thêm'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Nhãn tiếng Việt cho trạng thái slot.
class FmtSlot {
  static String status(String s) => switch (s.toUpperCase()) {
        'EMPTY' => 'Trống',
        'OCCUPIED' => 'Có xe',
        'MAINTENANCE' => 'Bảo trì',
        _ => s,
      };
}
