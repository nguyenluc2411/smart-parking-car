import 'package:dio/dio.dart';

/// Gắn `Authorization: Bearer <token>` vào mọi request (trừ khi chưa đăng nhập).
/// Token lấy realtime qua [tokenGetter] để luôn dùng giá trị mới nhất.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({required this.tokenGetter, this.onUnauthorized});

  final String? Function() tokenGetter;

  /// Gọi khi backend trả 401 — dùng để buộc logout ở tầng app.
  final void Function()? onUnauthorized;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = tokenGetter();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 401) {
      onUnauthorized?.call();
    }
    handler.next(err);
  }
}
