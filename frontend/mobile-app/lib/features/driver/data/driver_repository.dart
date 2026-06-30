import 'package:dio/dio.dart';

import '../../../core/network/api_response.dart';
import '../../../core/network/repo_helpers.dart';
import '../../billing/data/billing_models.dart';
import '../../parking/data/parking_models.dart';
import 'driver_models.dart';

/// Truy cập các endpoint tài xế (ADR-010) trải trên 3 service:
/// admin (auth + biển số), parking (my-sessions), billing (my-invoices + pay).
/// Chỉ dùng endpoint trong api-contracts.md.
class DriverRepository {
  DriverRepository({
    required Dio admin,
    required Dio parking,
    required Dio billing,
  })  : _admin = admin,
        _parking = parking,
        _billing = billing;

  final Dio _admin;
  final Dio _parking;
  final Dio _billing;

  // ---- admin-service: auth ----

  Future<OtpChallenge> requestOtp(String phone) => apiCall(
        () => _admin.post('/driver/auth/request-otp', data: {'phone': phone}),
        (data) => OtpChallenge.fromJson(data as Map<String, dynamic>),
      );

  Future<DriverAuthResult> verifyOtp({
    required String phone,
    required String code,
    String? fullName,
  }) =>
      apiCall(
        () => _admin.post('/driver/auth/verify-otp', data: {
          'phone': phone,
          'code': code,
          if (fullName != null && fullName.isNotEmpty) 'fullName': fullName,
        }),
        (data) => DriverAuthResult.fromJson(data as Map<String, dynamic>),
      );

  Future<void> logout({String? refreshToken}) => apiVoid(
        () => _admin.post('/driver/auth/logout',
            data: refreshToken == null ? null : {'refreshToken': refreshToken}),
      );

  // ---- admin-service: profile + plates ----

  Future<DriverProfile> getMe() => apiCall(
        () => _admin.get('/driver/me'),
        (data) => DriverProfile.fromJson(data as Map<String, dynamic>),
      );

  Future<DriverVehicle> addVehicle(String plateNumber) => apiCall(
        () => _admin.post('/driver/me/vehicles',
            data: {'plateNumber': plateNumber}),
        (data) => DriverVehicle.fromJson(data as Map<String, dynamic>),
      );

  Future<void> removeVehicle(String plateNumber) => apiVoid(
        () => _admin.delete('/driver/me/vehicles/$plateNumber'),
      );

  // ---- parking-service: my sessions ----

  Future<PageResult<SessionSummary>> mySessions({
    String? status,
    int page = 0,
    int size = 20,
  }) =>
      apiCall(
        () => _parking.get('/driver/sessions', queryParameters: {
          'status': ?status,
          'page': page,
          'size': size,
        }),
        (data) => PageResult.fromJson(
            data as Map<String, dynamic>, SessionSummary.fromJson),
      );

  Future<SessionDetail> sessionDetail(String id) => apiCall(
        () => _parking.get('/driver/sessions/$id'),
        (data) => SessionDetail.fromJson(data as Map<String, dynamic>),
      );

  // ---- billing-service: my invoices + online pay ----

  Future<PageResult<Invoice>> myInvoices({
    String? status,
    int page = 0,
    int size = 20,
  }) =>
      apiCall(
        () => _billing.get('/driver/invoices', queryParameters: {
          'status': ?status,
          'page': page,
          'size': size,
        }),
        (data) =>
            PageResult.fromJson(data as Map<String, dynamic>, Invoice.fromJson),
      );

  Future<Invoice> invoiceDetail(String invoiceId) => apiCall(
        () => _billing.get('/driver/invoices/$invoiceId'),
        (data) => Invoice.fromJson(data as Map<String, dynamic>),
      );

  Future<DriverPaymentResult> payInvoice(String invoiceId) => apiCall(
        () => _billing.post('/driver/invoices/$invoiceId/pay',
            data: {'method': 'ONLINE'}),
        (data) => DriverPaymentResult.fromJson(data as Map<String, dynamic>),
      );
}
