import 'package:dio/dio.dart';

import '../env/app_config.dart';
import 'auth_interceptor.dart';

/// Tạo các Dio instance cho từng service (gọi backend thật).
class DioFactory {
  DioFactory({required this.tokenGetter, this.onUnauthorized});

  final String? Function() tokenGetter;
  final void Function()? onUnauthorized;


  Dio _build(String serviceBaseUrl) {
    final dio = Dio(
      BaseOptions(
        baseUrl: serviceBaseUrl + AppConfig.apiPrefix,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
        contentType: Headers.jsonContentType,
        validateStatus: (status) => status != null && status < 400,
      ),
    );
    dio.interceptors.add(
      AuthInterceptor(tokenGetter: tokenGetter, onUnauthorized: onUnauthorized),
    );
    return dio;
  }

  Dio get parking => _build(AppConfig.parkingApiUrl);
  Dio get billing => _build(AppConfig.billingApiUrl);
  Dio get admin => _build(AppConfig.adminApiUrl);
}
