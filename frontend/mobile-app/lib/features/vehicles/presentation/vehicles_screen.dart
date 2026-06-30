import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

enum VehicleListKind { whitelist, blacklist }

final whitelistProvider = FutureProvider<List<Vehicle>>(
  (ref) => ref.watch(parkingRepositoryProvider).getWhitelist(),
);

final blacklistProvider = FutureProvider<List<Vehicle>>(
  (ref) => ref.watch(parkingRepositoryProvider).getBlacklist(),
);

class VehiclesScreen extends ConsumerWidget {
  const VehiclesScreen({super.key, this.initialTab});

  /// 'blacklist' opens the cấm tab directly (deep-link from a BLACKLIST_HIT alert).
  final String? initialTab;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 2,
      initialIndex: initialTab == 'blacklist' ? 1 : 0,
      child: Scaffold(
        appBar: const TabBar(
          tabs: [
            Tab(text: 'Whitelist'),
            Tab(text: 'Blacklist'),
          ],
        ),
        body: const TabBarView(
          children: [
            _VehicleList(kind: VehicleListKind.whitelist),
            _VehicleList(kind: VehicleListKind.blacklist),
          ],
        ),
        floatingActionButton: Builder(
          builder: (ctx) {
            final controller = DefaultTabController.of(ctx);
            // AnimatedBuilder để FAB rebuild khi đổi tab — nếu không, index luôn = 0
            // và nút "Thêm" luôn mở form whitelist dù đang ở tab Blacklist.
            return AnimatedBuilder(
              animation: controller,
              builder: (ctx2, _) {
                final kind = controller.index == 0
                    ? VehicleListKind.whitelist
                    : VehicleListKind.blacklist;
                return FloatingActionButton.extended(
                  onPressed: () => _showAdd(ctx2, ref, kind),
                  icon: const Icon(Icons.add),
                  label: Text(kind == VehicleListKind.whitelist
                      ? 'Thêm whitelist'
                      : 'Thêm blacklist'),
                );
              },
            );
          },
        ),
      ),
    );
  }

  Future<void> _showAdd(
      BuildContext context, WidgetRef ref, VehicleListKind kind) async {
    final added = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _AddVehicleSheet(kind: kind),
    );
    if (added == true) {
      // A plate has ONE classification and an add may re-classify it across lists,
      // so refresh BOTH so neither tab is left showing it (the "appears in both" bug).
      ref.invalidate(whitelistProvider);
      ref.invalidate(blacklistProvider);
    }
  }
}

class _VehicleList extends ConsumerWidget {
  const _VehicleList({required this.kind});
  final VehicleListKind kind;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final provider = kind == VehicleListKind.whitelist
        ? whitelistProvider
        : blacklistProvider;
    final vehicles = ref.watch(provider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(provider),
      child: AsyncView<List<Vehicle>>(
        value: vehicles,
        onRetry: () => ref.invalidate(provider),
        data: (list) {
          if (list.isEmpty) {
            return const EmptyView(message: 'Danh sách trống');
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: list.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _VehicleTile(vehicle: list[i], kind: kind),
          );
        },
      ),
    );
  }
}

class _VehicleTile extends ConsumerWidget {
  const _VehicleTile({required this.vehicle, required this.kind});
  final Vehicle vehicle;
  final VehicleListKind kind;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isBlacklist = vehicle.vehicleType == 'BLACKLIST';
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor:
              statusColor(context, vehicle.vehicleType).withValues(alpha: 0.15),
          child: Icon(isBlacklist ? Icons.block : Icons.verified_user,
              color: statusColor(context, vehicle.vehicleType)),
        ),
        title: Text(vehicle.plateNumber,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(
          '${vehicle.ownerName ?? '—'} · ${vehicle.note ?? ''}\n'
          'Tạo ${Fmt.date(vehicle.createdAt)}',
        ),
        isThreeLine: true,
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline),
          onPressed: () => _delete(context, ref),
        ),
      ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa khỏi danh sách?'),
        content: Text('Biển số ${vehicle.plateNumber}'),
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
    final repo = ref.read(parkingRepositoryProvider);
    try {
      if (kind == VehicleListKind.whitelist) {
        await repo.deleteWhitelist(vehicle.plateNumber);
        ref.invalidate(whitelistProvider);
      } else {
        await repo.deleteBlacklist(vehicle.plateNumber);
        ref.invalidate(blacklistProvider);
      }
      messenger.showSnackBar(const SnackBar(content: Text('Đã xóa')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }
}

class _AddVehicleSheet extends ConsumerStatefulWidget {
  const _AddVehicleSheet({required this.kind});
  final VehicleListKind kind;

  @override
  ConsumerState<_AddVehicleSheet> createState() => _AddVehicleSheetState();
}

class _AddVehicleSheetState extends ConsumerState<_AddVehicleSheet> {
  final _formKey = GlobalKey<FormState>();
  final _plate = TextEditingController();
  final _owner = TextEditingController();
  final _note = TextEditingController();
  bool _loading = false;
  String? _error;

  bool get _isBlacklist => widget.kind == VehicleListKind.blacklist;

  @override
  void dispose() {
    _plate.dispose();
    _owner.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _submit({bool force = false}) async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(parkingRepositoryProvider);
      final owner = _owner.text.trim().isEmpty ? null : _owner.text.trim();
      final note = _note.text.trim().isEmpty ? null : _note.text.trim();
      if (_isBlacklist) {
        await repo.addBlacklist(
            plateNumber: _plate.text.trim(),
            ownerName: owner,
            note: note,
            force: force);
      } else {
        await repo.addWhitelist(
            plateNumber: _plate.text.trim(),
            ownerName: owner,
            note: note,
            force: force);
      }
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      // Backend yêu cầu xác nhận chuyển list: 409 "RECLASSIFY:{loạiHiệnTại}:...".
      if (e.statusCode == 409 && e.message.startsWith('RECLASSIFY:')) {
        if (mounted) setState(() => _loading = false);
        final confirmed = await _confirmReclassify(e.message.split(':')[1]);
        if (confirmed == true) await _submit(force: true);
        return;
      }
      // FE tự quyết câu hiển thị: form thêm xe còn lại chỉ fail do biển số (400).
      setState(() => _error = e.statusCode == 400
          ? 'Biển số không hợp lệ. Ví dụ: 51F-12345'
          : 'Thêm thất bại, vui lòng thử lại');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Hỏi xác nhận khi biển đang nằm ở [fromType] (list đối diện) trước khi chuyển.
  Future<bool?> _confirmReclassify(String fromType) {
    final target = _isBlacklist ? 'BLACKLIST' : 'WHITELIST';
    return showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Chuyển danh sách?'),
        content: Text('Biển ${_plate.text.trim()} đang ở $fromType. '
            'Chuyển sang $target?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Hủy')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Chuyển')),
        ],
      ),
    );
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
            Text(_isBlacklist ? 'Thêm xe blacklist' : 'Thêm xe whitelist',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            TextFormField(
              controller: _plate,
              decoration: const InputDecoration(labelText: 'Biển số *'),
              textCapitalization: TextCapitalization.characters,
              validator: (v) =>
                  (v == null || v.trim().isEmpty) ? 'Nhập biển số' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _owner,
              decoration: const InputDecoration(labelText: 'Chủ xe'),
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _note,
              decoration: InputDecoration(
                  labelText: _isBlacklist ? 'Lý do cấm' : 'Ghi chú'),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style:
                      TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _loading ? null : () => _submit(),
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
