/// App flavors. Mỗi flavor có một entrypoint riêng (main_operator.dart /
/// main_driver.dart) và set [Flavor.current] trước khi chạy app.
enum Flavor {
  /// Phase 1 — nhân viên vận hành / quản trị. Dùng API hiện có.
  operator,

  /// Phase 2 — tài xế. Cần backend mở rộng (chưa khả dụng).
  driver,
}

class FlavorConfig {
  FlavorConfig._();

  static Flavor current = Flavor.operator;

  static bool get isOperator => current == Flavor.operator;
  static bool get isDriver => current == Flavor.driver;

  static String get appTitle => switch (current) {
        Flavor.operator => 'Smart Parking — Operator',
        Flavor.driver => 'Smart Parking — Driver',
      };
}
