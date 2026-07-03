import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/alerts/presentation/alerts_screen.dart';
import '../features/auth/presentation/auth_controller.dart';
import '../features/auth/presentation/login_screen.dart';
import '../features/billing/presentation/billing_screen.dart';
import '../features/billing/presentation/rates_screen.dart';
import '../features/dashboard/presentation/dashboard_screen.dart';
import '../features/gates/presentation/gates_screen.dart';
import '../features/reports/presentation/reports_screen.dart';
import '../features/sessions/presentation/session_detail_screen.dart';
import '../features/sessions/presentation/sessions_screen.dart';
import '../features/slots/presentation/slots_screen.dart';
import '../features/users/presentation/users_screen.dart';
import '../features/vehicles/presentation/vehicles_screen.dart';
import 'home_shell.dart';

const _adminOnly = {'/reports', '/rates', '/vehicles', '/slots', '/users'};

/// GoRouter có guard theo trạng thái đăng nhập + vai trò.
final routerProvider = Provider<GoRouter>((ref) {
  // Cầu nối Riverpod -> Listenable để router refresh khi auth đổi.
  final refresh = ValueNotifier(0);
  ref.listen(authControllerProvider, (_, _) => refresh.value++);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final loc = state.matchedLocation;

      if (auth.status == AuthStatus.unknown) {
        return loc == '/splash' ? null : '/splash';
      }

      final loggedIn = auth.status == AuthStatus.authenticated;
      if (!loggedIn) return loc == '/login' ? null : '/login';

      // Đã đăng nhập mà còn ở splash/login -> vào dashboard.
      if (loc == '/login' || loc == '/splash') return '/dashboard';

      // Chặn route ADMIN-only với OPERATOR.
      if (_adminOnly.any(loc.startsWith) && !auth.isAdmin) {
        return '/dashboard';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const _SplashScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      // Full-screen (ngoài shell) để có AppBar back riêng.
      GoRoute(
        path: '/sessions/:id',
        builder: (_, state) =>
            SessionDetailScreen(sessionId: state.pathParameters['id']!),
      ),
      ShellRoute(
        builder: (_, _, child) => HomeShell(child: child),
        routes: [
          GoRoute(
              path: '/dashboard', builder: (_, _) => const DashboardScreen()),
          GoRoute(path: '/alerts', builder: (_, _) => const AlertsScreen()),
          GoRoute(
              path: '/sessions',
              builder: (_, state) => SessionsScreen(
                  initialPlate: state.uri.queryParameters['plate'])),
          GoRoute(path: '/gates', builder: (_, _) => const GatesScreen()),
          GoRoute(
            path: '/billing',
            builder: (_, state) => BillingScreen(
              initialSessionId: state.uri.queryParameters['sessionId'],
            ),
          ),
          GoRoute(path: '/reports', builder: (_, _) => const ReportsScreen()),
          GoRoute(path: '/rates', builder: (_, _) => const RatesScreen()),
          GoRoute(
              path: '/vehicles',
              builder: (_, state) => VehiclesScreen(
                  initialTab: state.uri.queryParameters['tab'])),
          GoRoute(path: '/slots', builder: (_, _) => const SlotsScreen()),
          GoRoute(path: '/users', builder: (_, _) => const UsersScreen()),
        ],
      ),
    ],
  );
});

class _SplashScreen extends StatelessWidget {
  const _SplashScreen();
  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}
