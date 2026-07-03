import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../data/driver_models.dart';

enum DriverAuthStatus { unknown, authenticated, unauthenticated }

/// Trạng thái đăng nhập tài xế (token persist ở secure storage).
class DriverAuthState {
  final DriverAuthStatus status;
  final String? accessToken;

  const DriverAuthState({required this.status, this.accessToken});

  const DriverAuthState.unknown() : this(status: DriverAuthStatus.unknown);
  const DriverAuthState.signedOut()
      : this(status: DriverAuthStatus.unauthenticated);
}

/// Quản lý vòng đời đăng nhập tài xế (OTP qua SĐT — ADR-010).
/// UI gọi [requestOtp] rồi [verifyOtp]; dio đọc [DriverAuthState.accessToken].
class DriverAuthController extends Notifier<DriverAuthState> {
  @override
  DriverAuthState build() {
    _restore();
    return const DriverAuthState.unknown();
  }

  Future<void> _restore() async {
    final storage = ref.read(tokenStorageProvider);
    final token = await storage.accessToken;
    final role = await storage.role;
    if (token != null && token.isNotEmpty && role == 'DRIVER') {
      state = DriverAuthState(
          status: DriverAuthStatus.authenticated, accessToken: token);
    } else {
      state = const DriverAuthState.signedOut();
    }
  }

  /// Gửi OTP tới SĐT. Ném [ApiException] nếu lỗi (màn hình bắt và hiển thị).
  Future<OtpChallenge> requestOtp(String phone) {
    return ref.read(driverRepositoryProvider).requestOtp(phone);
  }

  /// Xác thực OTP. [fullName] bắt buộc ở lần đăng ký đầu. Lưu token và chuyển
  /// sang trạng thái authenticated.
  Future<void> verifyOtp({
    required String phone,
    required String code,
    String? fullName,
  }) async {
    final res = await ref.read(driverRepositoryProvider).verifyOtp(
          phone: phone,
          code: code,
          fullName: fullName,
        );
    await ref.read(tokenStorageProvider).save(
          accessToken: res.accessToken,
          refreshToken: res.refreshToken,
          role: res.role,
        );
    state = DriverAuthState(
      status: DriverAuthStatus.authenticated,
      accessToken: res.accessToken,
    );
  }

  Future<void> logout() async {
    final refresh = await ref.read(tokenStorageProvider).refreshToken;
    try {
      await ref.read(driverRepositoryProvider).logout(refreshToken: refresh);
    } catch (_) {
      // best-effort — vẫn đăng xuất phía client.
    }
    await ref.read(tokenStorageProvider).clear();
    state = const DriverAuthState.signedOut();
  }

  /// Gọi khi backend trả 401 (token hết hạn/không hợp lệ).
  void forceLogout() {
    ref.read(tokenStorageProvider).clear();
    state = const DriverAuthState.signedOut();
  }
}

final driverAuthControllerProvider =
    NotifierProvider<DriverAuthController, DriverAuthState>(
        DriverAuthController.new);
