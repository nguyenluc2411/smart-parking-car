import 'package:dio/dio.dart';

import '../../../core/network/api_response.dart';
import '../../../core/network/repo_helpers.dart';
import 'billing_models.dart';

/// Truy cập billing-service (:8082). Chỉ dùng endpoint trong api-contracts.md.
class BillingRepository {
  BillingRepository(this._dio);

  final Dio _dio;

  /// GET /billing/invoices — danh sách hóa đơn (OPERATOR/ADMIN), lọc + phân trang.
  Future<PageResult<Invoice>> listInvoices({
    String? status,
    String? plate,
    String? date,
    int page = 0,
    int size = 20,
  }) {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null && status.isNotEmpty) query['status'] = status;
    if (plate != null && plate.isNotEmpty) query['plate'] = plate;
    if (date != null && date.isNotEmpty) query['date'] = date;
    return apiCall(
      () => _dio.get('/billing/invoices', queryParameters: query),
      (data) => PageResult.fromJson(
        data as Map<String, dynamic>,
        Invoice.fromJson,
      ),
    );
  }

  Future<Invoice> getInvoiceBySession(String sessionId) => apiCall(
        () => _dio.get('/billing/sessions/$sessionId'),
        (data) => Invoice.fromJson(data as Map<String, dynamic>),
      );

  /// POST /billing/sessions/{id}/pay. method ∈ CASH | ... (theo backend).
  Future<PaymentResult> pay({
    required String sessionId,
    required String method,
    required num amountPaid,
    String? note,
  }) =>
      apiCall(
        () => _dio.post('/billing/sessions/$sessionId/pay', data: {
          'method': method,
          'amountPaid': amountPaid,
          'note': note,
        }),
        (data) => PaymentResult.fromJson(data as Map<String, dynamic>),
      );

  Future<Rate> getRates() => apiCall(
        () => _dio.get('/billing/rates'),
        (data) => Rate.fromJson(data as Map<String, dynamic>),
      );

  /// PUT /billing/rates (ADMIN).
  Future<Rate> updateRates({
    required double ratePerMin,
    required double peakMultiplier,
    required num overnightFlat,
    required num minCharge,
  }) =>
      apiCall(
        () => _dio.put('/billing/rates', data: {
          'ratePerMin': ratePerMin,
          'peakMultiplier': peakMultiplier,
          'overnightFlat': overnightFlat,
          'minCharge': minCharge,
        }),
        (data) => Rate.fromJson(data as Map<String, dynamic>),
      );

  Future<DailyReport> getDailyReport({String? date}) => apiCall(
        () => _dio.get('/billing/report/daily',
            queryParameters: date == null ? null : {'date': date}),
        (data) => DailyReport.fromJson(data as Map<String, dynamic>),
      );

  Future<MonthlyReport> getMonthlyReport({String? month}) => apiCall(
        () => _dio.get('/billing/report/monthly',
            queryParameters: month == null ? null : {'month': month}),
        (data) => MonthlyReport.fromJson(data as Map<String, dynamic>),
      );
}
