import 'package:dio/dio.dart';

/// Lỗi nghiệp vụ đã chuẩn hóa từ format error của backend:
/// `{ success:false, error:{ code, message, field } }`.
class ApiException implements Exception {
  final String code;
  final String message;
  final String? field;
  final int? statusCode;

  const ApiException({
    required this.code,
    required this.message,
    this.field,
    this.statusCode,
  });

  /// Thông báo tiếng Việt theo mã lỗi backend (app dành cho người Việt). Mã không
  /// có trong bảng sẽ giữ nguyên message gốc từ server.
  static const _vnByCode = <String, String>{
    'INVALID_CREDENTIALS': 'Sai tên đăng nhập hoặc mật khẩu',
    'UNAUTHORIZED': 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại',
    'INVALID_TOKEN': 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại',
    'FORBIDDEN': 'Bạn không có quyền thực hiện thao tác này',
    'ACCESS_DENIED': 'Bạn không có quyền thực hiện thao tác này',
    'NOT_FOUND': 'Không tìm thấy dữ liệu',
    'VALIDATION_ERROR': 'Dữ liệu nhập chưa hợp lệ',
    'TOO_MANY_ATTEMPTS': 'Bạn thử quá nhiều lần, vui lòng lấy mã mới',
    'INTERNAL_ERROR': 'Hệ thống gặp sự cố, vui lòng thử lại sau',
  };

  /// Dựng từ [DioException] — bóc tách body error nếu có, nếu không thì map
  /// theo loại lỗi mạng/timeout.
  factory ApiException.fromDio(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic> && data['error'] is Map<String, dynamic>) {
      final err = data['error'] as Map<String, dynamic>;
      final code = err['code'] as String? ?? 'UNKNOWN';
      final serverMsg = err['message'] as String?;
      return ApiException(
        code: code,
        message: _vnByCode[code] ?? serverMsg ?? 'Đã có lỗi xảy ra',
        field: err['field'] as String?,
        statusCode: e.response?.statusCode,
      );
    }

    final message = switch (e.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout =>
        'Kết nối quá thời gian, vui lòng thử lại',
      DioExceptionType.connectionError =>
        'Không kết nối được máy chủ. Kiểm tra mạng hoặc địa chỉ API.',
      _ => 'Đã có lỗi xảy ra (HTTP ${e.response?.statusCode ?? '-'})',
    };
    return ApiException(
      code: 'NETWORK_ERROR',
      message: message,
      statusCode: e.response?.statusCode,
    );
  }

  @override
  String toString() => message;
}
