import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/theme_controller.dart';
import '../../../core/widgets/theme_mode_picker.dart';
import 'driver_auth_controller.dart';
import 'my_invoices_screen.dart';
import 'my_sessions_screen.dart';
import 'my_vehicles_screen.dart';
import 'reservations_screen.dart';

/// Khung chính sau khi tài xế đăng nhập: 4 tab (Phiên · Đặt chỗ · Hoá đơn · Biển số).
class DriverHome extends ConsumerStatefulWidget {
  const DriverHome({super.key});

  @override
  ConsumerState<DriverHome> createState() => _DriverHomeState();
}

class _DriverHomeState extends ConsumerState<DriverHome> {
  int _index = 0;

  static const _titles = [
    'Phiên của tôi',
    'Đặt chỗ',
    'Hoá đơn của tôi',
    'Biển số của tôi',
  ];
  static const _tabs = [
    MySessionsScreen(),
    ReservationsScreen(),
    MyInvoicesScreen(),
    MyVehiclesScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_index]),
        actions: [
          IconButton(
            icon: Icon(themeModeIcon(ref.watch(themeModeProvider))),
            tooltip: 'Giao diện',
            onPressed: () => showThemeModePicker(context, ref),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Đăng xuất',
            onPressed: () =>
                ref.read(driverAuthControllerProvider.notifier).logout(),
          ),
        ],
      ),
      body: IndexedStack(index: _index, children: _tabs),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(
              icon: Icon(Icons.local_parking_outlined),
              selectedIcon: Icon(Icons.local_parking),
              label: 'Phiên'),
          NavigationDestination(
              icon: Icon(Icons.receipt_long_outlined),
              selectedIcon: Icon(Icons.receipt_long),
              label: 'Hoá đơn'),
          NavigationDestination(
              icon: Icon(Icons.directions_car_outlined),
              selectedIcon: Icon(Icons.directions_car),
              label: 'Biển số'),
        ],
      ),
    );
  }
}
