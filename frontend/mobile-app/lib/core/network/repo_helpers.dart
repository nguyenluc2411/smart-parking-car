import 'package:dio/dio.dart';

import 'api_exception.dart';

/// Gọi API + bóc envelope `{success,data}` + map sang model.
/// Mọi [DioException] được chuẩn hóa thành [ApiException].
Future<T> apiCall<T>(
  Future<Response<dynamic>> Function() request,
  T Function(Object? data) mapData,
) async {
  try {
    final res = await request();
    final body = res.data;
    if (body is Map<String, dynamic> && body.containsKey('data')) {
      return mapData(body['data']);
    }
    // Một số endpoint (204/no-content) không có envelope.
    return mapData(body);
  } on DioException catch (e) {
    throw ApiException.fromDio(e);
  }
}

/// Cho các call không cần dữ liệu trả về (DELETE, override, logout).
Future<void> apiVoid(Future<Response<dynamic>> Function() request) async {
  try {
    await request();
  } on DioException catch (e) {
    throw ApiException.fromDio(e);
  }
}
