import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'app/flavor.dart';

/// Entrypoint mặc định (cho `flutter run` không chỉ định -t).
/// Mặc định chạy flavor OPERATOR. Flavor driver dùng lib/main_driver.dart.
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  FlavorConfig.current = Flavor.operator;
  runApp(const ProviderScope(child: OperatorApp()));
}
