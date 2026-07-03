import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'app/flavor.dart';

/// Entrypoint flavor OPERATOR/ADMIN.
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  FlavorConfig.current = Flavor.operator;
  runApp(const ProviderScope(child: OperatorApp()));
}
