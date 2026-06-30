import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../core/network/dio_client.dart';
import '../core/storage/token_storage.dart';
import '../features/admin/data/admin_repository.dart';
import '../features/auth/presentation/auth_controller.dart';
import '../features/billing/data/billing_repository.dart';
import '../features/driver/data/driver_repository.dart';
import '../features/driver/presentation/driver_auth_controller.dart';
import '../features/parking/data/parking_repository.dart';

/// Đồ thị dependency (DI) toàn app — không tạo instance trực tiếp trong UI.

final secureStorageProvider = Provider<FlutterSecureStorage>(
  (ref) => const FlutterSecureStorage(),
);

final tokenStorageProvider = Provider<TokenStorage>(
  (ref) => TokenStorage(ref.watch(secureStorageProvider)),
);

/// Token được đọc lazily trong closure (tại thời điểm request) nên KHÔNG tạo
/// vòng phụ thuộc với authController.
final dioFactoryProvider = Provider<DioFactory>((ref) {
  return DioFactory(
    tokenGetter: () => ref.read(authControllerProvider).accessToken,
    onUnauthorized: () => ref.read(authControllerProvider.notifier).forceLogout(),
  );
});

final parkingRepositoryProvider = Provider<ParkingRepository>(
  (ref) => ParkingRepository(ref.watch(dioFactoryProvider).parking),
);

final billingRepositoryProvider = Provider<BillingRepository>(
  (ref) => BillingRepository(ref.watch(dioFactoryProvider).billing),
);

final adminRepositoryProvider = Provider<AdminRepository>(
  (ref) => AdminRepository(ref.watch(dioFactoryProvider).admin),
);

/// Flavor `driver`: dio đọc token từ [driverAuthControllerProvider] (tách khỏi
/// luồng operator). Token đọc lazily trong closure để không tạo vòng phụ thuộc.
final driverDioFactoryProvider = Provider<DioFactory>((ref) {
  return DioFactory(
    tokenGetter: () => ref.read(driverAuthControllerProvider).accessToken,
    onUnauthorized: () =>
        ref.read(driverAuthControllerProvider.notifier).forceLogout(),
  );
});

final driverRepositoryProvider = Provider<DriverRepository>((ref) {
  final factory = ref.watch(driverDioFactoryProvider);
  return DriverRepository(
    admin: factory.admin,
    parking: factory.parking,
    billing: factory.billing,
  );
});
