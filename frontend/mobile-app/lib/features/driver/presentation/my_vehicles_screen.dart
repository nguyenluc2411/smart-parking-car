import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../data/driver_models.dart';

/// Hồ sơ tài xế + biển số đã khai (admin GET /driver/me).
final driverProfileProvider =
    FutureProvider.autoDispose<DriverProfile>((ref) {
  return ref.watch(driverRepositoryProvider).getMe();
});

class MyVehiclesScreen extends ConsumerWidget {
  const MyVehiclesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(driverProfileProvider);
    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(driverProfileProvider),
        child: AsyncView<DriverProfile>(
          value: async,
          onRetry: () => ref.invalidate(driverProfileProvider),
          data: (profile) {
            if (profile.vehicles.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 120),
                  EmptyView(
                      message: 'Chưa khai biển số nào',
                      icon: Icons.directions_car_outlined),
                ],
              );
            }
            return ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: profile.vehicles.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (_, i) =>
                  _VehicleCard(vehicle: profile.vehicles[i]),
            );
          },
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _addPlate(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Thêm biển số'),
      ),
    );
  }

  Future<void> _addPlate(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController();
    final messenger = ScaffoldMessenger.of(context);
    final plate = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thêm biển số'),
        content: TextField(
          controller: controller,
          autofocus: true,
          textCapitalization: TextCapitalization.characters,
          decoration: const InputDecoration(
            labelText: 'Biển số',
            hintText: '51F-123.45',
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Huỷ')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, controller.text.trim()),
            child: const Text('Thêm'),
          ),
        ],
      ),
    );
    if (plate == null || plate.isEmpty) return;

    try {
      await ref.read(driverRepositoryProvider).addVehicle(plate);
      ref.invalidate(driverProfileProvider);
      messenger.showSnackBar(const SnackBar(
          content: Text('Đã khai biển số — chờ operator/admin duyệt')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Lỗi: ${e.message}')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Thêm biển thất bại, thử lại sau.')));
    }
  }
}

class _VehicleCard extends ConsumerWidget {
  const _VehicleCard({required this.vehicle});

  final DriverVehicle vehicle;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final verified = vehicle.verified;
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: verified
              ? Colors.green.shade100
              : Colors.orange.shade100,
          child: Icon(verified ? Icons.verified : Icons.hourglass_top,
              color: verified ? Colors.green.shade800 : Colors.orange.shade800),
        ),
        title: Text(vehicle.plateNumber,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(verified
            ? 'Đã duyệt · ${Fmt.date(vehicle.createdAt)}'
            : 'Chờ duyệt · ${Fmt.date(vehicle.createdAt)}'),
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline),
          tooltip: 'Xoá biển',
          onPressed: () => _remove(context, ref),
        ),
      ),
    );
  }

  Future<void> _remove(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xoá biển số'),
        content: Text('Xoá ${vehicle.plateNumber} khỏi tài khoản?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Huỷ')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Xoá')),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      await ref.read(driverRepositoryProvider).removeVehicle(vehicle.plateNumber);
      ref.invalidate(driverProfileProvider);
      messenger
          .showSnackBar(const SnackBar(content: Text('Đã xoá biển số')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Lỗi: ${e.message}')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Xoá biển thất bại, thử lại sau.')));
    }
  }
}
