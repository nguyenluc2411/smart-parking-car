import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/app_theme.dart';
import '../core/theme/theme_controller.dart';
import '../core/widgets/brand_logo.dart';
import '../core/widgets/theme_mode_picker.dart';
import '../features/alerts/presentation/alerts_screen.dart';
import '../features/auth/presentation/auth_controller.dart';

/// Một mục điều hướng trong Drawer.
class NavItem {
  final String path;
  final String label;
  final IconData icon;
  final bool adminOnly;
  const NavItem(this.path, this.label, this.icon, {this.adminOnly = false});
}

const navItems = <NavItem>[
  NavItem('/dashboard', 'Tổng quan', Icons.dashboard_outlined),
  NavItem('/alerts', 'Cảnh báo', Icons.warning_amber_outlined),
  NavItem('/sessions', 'Phiên xe', Icons.directions_car_outlined),
  NavItem('/gates', 'Cổng', Icons.sensor_door_outlined),
  NavItem('/billing', 'Hóa đơn', Icons.receipt_long_outlined),
  NavItem('/reports', 'Báo cáo', Icons.bar_chart, adminOnly: true),
  NavItem('/rates', 'Bảng giá', Icons.price_change_outlined, adminOnly: true),
  NavItem('/slots', 'Bãi xe / Slot', Icons.grid_view_outlined,
      adminOnly: true),
  NavItem('/vehicles', 'Whitelist / Blacklist', Icons.verified_user_outlined,
      adminOnly: true),
  NavItem('/users', 'Người dùng', Icons.people_outline, adminOnly: true),
];

/// Khung chính: AppBar + Drawer bao quanh nội dung từng trang.
class HomeShell extends ConsumerWidget {
  const HomeShell({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final auth = ref.watch(authControllerProvider);

    // App-wide live alerts: pop a SnackBar for CRITICAL ones on any page (operator on the floor).
    ref.listen(alertStreamProvider, (_, next) {
      next.whenData((alert) {
        if (!alert.isCritical) return;
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          backgroundColor: Colors.red,
          content: Text('⚠ ${alert.message}'
              '${alert.plateNumber != null ? ' (${alert.plateNumber})' : ''}'),
        ));
      });
    });

    final location = GoRouterState.of(context).uri.path;
    final current = navItems.firstWhere(
      (n) => location.startsWith(n.path),
      orElse: () => navItems.first,
    );
    final visible =
        navItems.where((n) => !n.adminOnly || auth.isAdmin).toList();

    return Scaffold(
      appBar: AppBar(
        title: Text(current.label),
      ),
      drawer: Drawer(
        child: SafeArea(
          child: Column(
            children: [
              Container(
                width: double.infinity,
                padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
                decoration: const BoxDecoration(gradient: AppTheme.brandGradient),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.18),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: const BrandLogo.mono(size: 34),
                    ),
                    const SizedBox(height: 16),
                    const Text('Smart Parking',
                        style: TextStyle(
                            color: Colors.white,
                            fontSize: 20,
                            fontWeight: FontWeight.bold)),
                    const SizedBox(height: 2),
                    Text(
                      'Vai trò: ${auth.role ?? '—'}',
                      style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.85),
                          fontSize: 13),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 8),
                  children: [
                    for (final item in visible)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: ListTile(
                          leading: Icon(item.icon),
                          title: Text(item.label),
                          selected: location.startsWith(item.path),
                          onTap: () {
                            Navigator.pop(context);
                            context.go(item.path);
                          },
                        ),
                      ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Builder(
                builder: (context) {
                  final mode = ref.watch(themeModeProvider);
                  return ListTile(
                    leading: Icon(themeModeIcon(mode)),
                    title: const Text('Giao diện'),
                    subtitle: Text(themeModeLabel(mode)),
                    onTap: () => showThemeModePicker(context, ref),
                  );
                },
              ),
              const Divider(height: 1),
              ListTile(
                leading: Icon(Icons.logout, color: theme.colorScheme.error),
                title: Text('Đăng xuất',
                    style: TextStyle(color: theme.colorScheme.error)),
                onTap: () async {
                  Navigator.pop(context);
                  await ref.read(authControllerProvider.notifier).logout();
                },
              ),
            ],
          ),
        ),
      ),
      body: child,
    );
  }
}
