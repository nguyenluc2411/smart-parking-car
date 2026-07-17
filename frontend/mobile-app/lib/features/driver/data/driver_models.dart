// Models khớp docs/api-contracts.md — "Phase 2 — Driver" (ADR-010).
// KHÔNG tự thêm field ngoài contract.

/// Response của POST /driver/auth/request-otp.
class OtpChallenge {
  final String channel; // SMS
  final int expiresIn; // giây
  final int resendAfter; // giây

  const OtpChallenge({
    required this.channel,
    required this.expiresIn,
    required this.resendAfter,
  });

  factory OtpChallenge.fromJson(Map<String, dynamic> j) => OtpChallenge(
        channel: j['channel'] as String,
        expiresIn: j['expiresIn'] as int,
        resendAfter: j['resendAfter'] as int,
      );
}

/// Response của POST /driver/auth/verify-otp và /refresh. role luôn là "DRIVER".
class DriverAuthResult {
  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final String role;

  const DriverAuthResult({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.role,
  });

  factory DriverAuthResult.fromJson(Map<String, dynamic> j) => DriverAuthResult(
        accessToken: j['accessToken'] as String,
        refreshToken: j['refreshToken'] as String,
        expiresIn: j['expiresIn'] as int,
        role: j['role'] as String,
      );
}

/// Một biển số gắn với tài khoản tài xế (verified = đã được operator/admin duyệt).
class DriverVehicle {
  final String id;
  final String plateNumber;
  final bool verified;
  final String createdAt;

  const DriverVehicle({
    required this.id,
    required this.plateNumber,
    required this.verified,
    required this.createdAt,
  });

  factory DriverVehicle.fromJson(Map<String, dynamic> j) => DriverVehicle(
        id: j['id'] as String,
        plateNumber: j['plateNumber'] as String,
        verified: j['verified'] as bool,
        createdAt: j['createdAt'] as String,
      );
}

/// Response của GET /driver/me.
class DriverProfile {
  final String id;
  final String phone;
  final String fullName;
  final List<DriverVehicle> vehicles;

  const DriverProfile({
    required this.id,
    required this.phone,
    required this.fullName,
    required this.vehicles,
  });

  factory DriverProfile.fromJson(Map<String, dynamic> j) => DriverProfile(
        id: j['id'] as String,
        phone: j['phone'] as String,
        fullName: j['fullName'] as String,
        vehicles: (j['vehicles'] as List<dynamic>? ?? const [])
            .map((e) => DriverVehicle.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );
}

/// Response của POST /driver/invoices/{id}/pay (thanh toán online).
class DriverPaymentResult {
  final String paymentId;
  final String invoiceId;
  final String status; // PAID
  final String method; // ONLINE
  final num amountPaid;
  final String? qrData; // null ở mock/RBL (chưa tích hợp cổng thật)
  final String? paidAt;

  const DriverPaymentResult({
    required this.paymentId,
    required this.invoiceId,
    required this.status,
    required this.method,
    required this.amountPaid,
    required this.qrData,
    required this.paidAt,
  });

  factory DriverPaymentResult.fromJson(Map<String, dynamic> j) =>
      DriverPaymentResult(
        paymentId: j['paymentId'] as String,
        invoiceId: j['invoiceId'] as String,
        status: j['status'] as String,
        method: j['method'] as String,
        amountPaid: j['amountPaid'] as num,
        qrData: j['qrData'] as String?,
        paidAt: j['paidAt'] as String?,
      );
}
