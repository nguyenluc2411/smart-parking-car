import 'package:dio/dio.dart';

import '../../../core/network/api_response.dart';
import '../../../core/network/repo_helpers.dart';
import 'admin_models.dart';

/// Truy cập admin-service (:8083). Chỉ dùng endpoint trong api-contracts.md.
class AdminRepository {
  AdminRepository(this._dio);

  final Dio _dio;

  Future<AuthResult> login({
    required String username,
    required String password,
  }) =>
      apiCall(
        () => _dio.post('/auth/login',
            data: {'username': username, 'password': password}),
        (data) => AuthResult.fromJson(data as Map<String, dynamic>),
      );

  Future<void> logout({String? refreshToken}) => apiVoid(
        () => _dio.post('/auth/logout',
            data: refreshToken == null ? null : {'refreshToken': refreshToken}),
      );

  Future<PageResult<AppUser>> getUsers({int page = 0, int size = 20}) =>
      apiCall(
        () => _dio.get('/users', queryParameters: {'page': page, 'size': size}),
        (data) =>
            PageResult.fromJson(data as Map<String, dynamic>, AppUser.fromJson),
      );

  /// PUT /users/{id}/activate — bật lại tài khoản đã vô hiệu hóa.
  Future<AppUser> activateUser(String id) => apiCall(
        () => _dio.put('/users/$id/activate'),
        (data) => AppUser.fromJson(data as Map<String, dynamic>),
      );

  /// DELETE /users/{id} — vô hiệu hóa tài khoản (soft-delete).
  Future<void> deactivateUser(String id) =>
      apiVoid(() => _dio.delete('/users/$id'));
}
