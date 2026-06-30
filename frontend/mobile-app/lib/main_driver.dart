import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/flavor.dart';
import 'features/driver/presentation/driver_app.dart';

/// Entrypoint flavor DRIVER (Phase 2 — placeholder).
/// Chạy: flutter run -t lib/main_driver.dart
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  FlavorConfig.current = Flavor.driver;
  runApp(const ProviderScope(child: DriverApp()));
}
