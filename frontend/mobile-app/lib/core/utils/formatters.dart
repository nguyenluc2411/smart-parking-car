import 'package:intl/intl.dart';

/// Định dạng hiển thị dùng chung (logic UI thuần — không gọi API).
class Fmt {
  Fmt._();

  static final _currency =
      NumberFormat.currency(locale: 'vi_VN', symbol: '₫', decimalDigits: 0);
  static final _dateTime = DateFormat('dd/MM/yyyy HH:mm');
  static final _date = DateFormat('dd/MM/yyyy');

  /// Tiền VND: 50000 -> "50.000 ₫".
  static String currency(num? amount) =>
      amount == null ? '—' : _currency.format(amount);

  /// ISO-8601 (UTC) -> giờ địa phương "dd/MM/yyyy HH:mm".
  static String dateTime(String? iso) {
    if (iso == null) return '—';
    final dt = DateTime.tryParse(iso);
    return dt == null ? iso : _dateTime.format(dt.toLocal());
  }

  static String date(String? iso) {
    if (iso == null) return '—';
    final dt = DateTime.tryParse(iso);
    return dt == null ? iso : _date.format(dt.toLocal());
  }

  /// Giây -> "2h 15m" / "45m". Dùng cho duration của session.
  static String duration(int? seconds) {
    if (seconds == null) return '—';
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) return '${h}h ${m}m';
    return '${m}m';
  }

  /// Phút -> "2h 15m".
  static String minutes(int? minutes) {
    if (minutes == null) return '—';
    return duration(minutes * 60);
  }

  /// 0.64 -> "64%".
  static String percent(num? ratio) =>
      ratio == null ? '—' : '${(ratio * 100).toStringAsFixed(0)}%';

  // ----- Nhãn tiếng Việt cho enum/status từ backend -----
  // Giá trị enum giữ nguyên tiếng Anh khi gửi/lọc API; chỉ dịch khi HIỂN THỊ.
  // Nếu gặp giá trị lạ (ngoài contract) -> trả nguyên văn để không nuốt dữ liệu.

  static String _map(Map<String, String> dict, String? raw) {
    if (raw == null || raw.isEmpty) return '—';
    return dict[raw.toUpperCase()] ?? raw;
  }

  /// Trạng thái phiên: PENDING | ACTIVE | CLOSED | CANCELLED | REQUIRES_ATTENTION.
  static String sessionStatus(String? s) => _map(const {
        'PENDING': 'Chờ vào',
        'ACTIVE': 'Đang gửi',
        'CLOSED': 'Đã ra',
        'CANCELLED': 'Đã hủy',
        'REQUIRES_ATTENTION': 'Cần xử lý',
      }, s);

  /// Trạng thái cổng/barie: OPEN | CLOSED | ERROR | MAINTENANCE.
  static String gateStatus(String? s) => _map(const {
        'OPEN': 'Đang mở',
        'CLOSED': 'Đang đóng',
        'ERROR': 'Lỗi',
        'MAINTENANCE': 'Bảo trì',
      }, s);

  /// Trạng thái hóa đơn: PENDING | PAID | CANCELLED.
  static String invoiceStatus(String? s) => _map(const {
        'PENDING': 'Chưa thanh toán',
        'PAID': 'Đã thanh toán',
        'CANCELLED': 'Đã hủy',
      }, s);

  /// Vai trò người dùng: ADMIN | OPERATOR.
  static String role(String? s) => _map(const {
        'ADMIN': 'Quản trị',
        'OPERATOR': 'Nhân viên',
      }, s);

  /// Trạng thái lượt đặt chỗ: HELD | FULFILLED | CANCELLED | EXPIRED.
  static String reservationStatus(String? s) => _map(const {
        'HELD': 'Đang giữ chỗ',
        'FULFILLED': 'Đã vào bãi',
        'CANCELLED': 'Đã hủy',
        'EXPIRED': 'Đã hết hạn (bỏ hẹn)',
      }, s);

  /// Loại xe trong danh sách: WHITELIST | BLACKLIST.
  static String vehicleType(String? s) => _map(const {
        'WHITELIST': 'Ưu tiên',
        'BLACKLIST': 'Cấm',
      }, s);

  /// Loại ngày áp khung giá: ALL | WEEKDAY | WEEKEND.
  static String dayType(String? s) => _map(const {
        'ALL': 'Mọi ngày',
        'WEEKDAY': 'Ngày thường',
        'WEEKEND': 'Cuối tuần',
      }, s);

  /// Hướng cổng: IN | OUT.
  static String direction(String? s) => _map(const {
        'IN': 'Vào',
        'OUT': 'Ra',
      }, s);

  /// Lệnh điều khiển barie: OPEN | CLOSE.
  static String gateCommand(String? s) => _map(const {
        'OPEN': 'Mở',
        'CLOSE': 'Đóng',
      }, s);

  /// Phương thức thanh toán: CASH | TRANSFER | EWALLET | ONLINE.
  static String paymentMethod(String? s) => _map(const {
        'CASH': 'Tiền mặt',
        'TRANSFER': 'Chuyển khoản',
        'EWALLET': 'Ví điện tử',
        'ONLINE': 'Trực tuyến',
      }, s);
}
