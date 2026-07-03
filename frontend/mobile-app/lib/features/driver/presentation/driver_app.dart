import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/flavor.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/theme/theme_controller.dart';
import 'driver_auth_controller.dart';
import 'driver_home.dart';
import 'driver_login_screen.dart';

/// App flavor `driver` (Phase 2 — ADR-010). Tài xế đăng nhập bằng OTP (SĐT),
/// xem phiên/hoá đơn của xe mình và thanh toán online.
///
/// Điều hướng theo trạng thái auth (không dùng go_router cho app 3-tab này):
/// unknown → splash · unauthenticated → login · authenticated → home.
class DriverApp extends ConsumerWidget {
  const DriverApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(driverAuthControllerProvider);
    final Widget home = switch (auth.status) {
      DriverAuthStatus.unknown => const _SplashScreen(),
      DriverAuthStatus.unauthenticated => const DriverLoginScreen(),
      DriverAuthStatus.authenticated => const DriverHome(),
    };

    return MaterialApp(
      title: FlavorConfig.appTitle,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: ref.watch(themeModeProvider),
      debugShowCheckedModeBanner: false,
      home: home,
    );
  }
}

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}
