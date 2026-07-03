import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';

enum AuthStatus { unknown, authenticated, unauthenticated }

/// Trạng thái đăng nhập giữ trong bộ nhớ (token cũng được persist ở secure storage).
class AuthState {
  final AuthStatus status;
  final String? role; // ADMIN | OPERATOR
  final String? accessToken;

  const AuthState({required this.status, this.role, this.accessToken});

  bool get isAdmin => role == 'ADMIN';

  const AuthState.unknown() : this(status: AuthStatus.unknown);
  const AuthState.signedOut() : this(status: AuthStatus.unauthenticated);
}

/// Quản lý vòng đời đăng nhập. UI gọi [login]/[logout]; dio đọc [AuthState.accessToken].
class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    _restore();
    return const AuthState.unknown();
  }

  Future<void> _restore() async {
    final storage = ref.read(tokenStorageProvider);
    final token = await storage.accessToken;
    final role = await storage.role;
    if (token != null && token.isNotEmpty) {
      state =
          AuthState(status: AuthStatus.authenticated, role: role, accessToken: token);
    } else {
      state = const AuthState.signedOut();
    }
  }

  /// Ném [ApiException] nếu sai thông tin — màn hình login bắt và hiển thị.
  Future<void> login({
    required String username,
    required String password,
  }) async {
    final res = await ref
        .read(adminRepositoryProvider)
        .login(username: username, password: password);
    await ref.read(tokenStorageProvider).save(
          accessToken: res.accessToken,
          refreshToken: res.refreshToken,
          role: res.role,
        );
    state = AuthState(
      status: AuthStatus.authenticated,
      role: res.role,
      accessToken: res.accessToken,
    );
  }

  Future<void> logout() async {
    final refresh = await ref.read(tokenStorageProvider).refreshToken;
    try {
      await ref.read(adminRepositoryProvider).logout(refreshToken: refresh);
    } catch (_) {
      // best-effort — vẫn đăng xuất phía client.
    }
    await ref.read(tokenStorageProvider).clear();
    state = const AuthState.signedOut();
  }

  /// Gọi khi backend trả 401 (token hết hạn/không hợp lệ).
  void forceLogout() {
    ref.read(tokenStorageProvider).clear();
    state = const AuthState.signedOut();
  }
}

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);
