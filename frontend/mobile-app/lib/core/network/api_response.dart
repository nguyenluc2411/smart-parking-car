/// Bao response chuẩn của backend: `{ success, data, message, timestamp }`.
/// Khớp `docs/api-contracts.md`.
class ApiResponse<T> {
  final bool success;
  final T data;
  final String? message;
  final String? timestamp;

  const ApiResponse({
    required this.success,
    required this.data,
    this.message,
    this.timestamp,
  });

  /// Parse envelope rồi map phần `data` qua [fromData].
  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? data) fromData,
  ) {
    return ApiResponse<T>(
      success: json['success'] as bool? ?? false,
      data: fromData(json['data']),
      message: json['message'] as String?,
      timestamp: json['timestamp'] as String?,
    );
  }
}

/// Trang dữ liệu chuẩn của Spring Data (`content`, `totalElements`, ...).
/// Đặt tên `PageResult` để tránh đụng với `Page` của Flutter (navigator).
class PageResult<T> {
  final List<T> content;
  final int totalElements;
  final int totalPages;
  final int page;
  final int size;

  const PageResult({
    required this.content,
    required this.totalElements,
    required this.totalPages,
    required this.page,
    required this.size,
  });

  factory PageResult.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromItem,
  ) {
    final rawContent = (json['content'] as List<dynamic>? ?? const []);
    return PageResult<T>(
      content: rawContent
          .map((e) => fromItem(e as Map<String, dynamic>))
          .toList(growable: false),
      totalElements: json['totalElements'] as int? ?? rawContent.length,
      totalPages: json['totalPages'] as int? ?? 1,
      page: json['page'] as int? ?? 0,
      size: json['size'] as int? ?? rawContent.length,
    );
  }
}
