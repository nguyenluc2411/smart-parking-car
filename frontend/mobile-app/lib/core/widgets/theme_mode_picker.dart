import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../theme/theme_controller.dart';

/// Mở hộp thoại chọn chế độ giao diện (Theo hệ thống / Sáng / Tối).
Future<void> showThemeModePicker(BuildContext context, WidgetRef ref) {
  final current = ref.read(themeModeProvider);
  return showDialog<void>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: const Text('Giao diện'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final mode in ThemeMode.values)
            ListTile(
              leading: Icon(themeModeIcon(mode)),
              title: Text(themeModeLabel(mode)),
              trailing: mode == current
                  ? Icon(Icons.check,
                      color: Theme.of(ctx).colorScheme.primary)
                  : null,
              onTap: () {
                ref.read(themeModeProvider.notifier).set(mode);
                Navigator.pop(ctx);
              },
            ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('Đóng'),
        ),
      ],
    ),
  );
}

/// Icon tương ứng chế độ hiện tại (cho nút trên AppBar).
IconData themeModeIcon(ThemeMode mode) => switch (mode) {
      ThemeMode.light => Icons.light_mode_outlined,
      ThemeMode.dark => Icons.dark_mode_outlined,
      ThemeMode.system => Icons.brightness_auto_outlined,
    };
