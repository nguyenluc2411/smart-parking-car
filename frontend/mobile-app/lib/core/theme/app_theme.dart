import 'package:flutter/material.dart';

class AppTheme {
  AppTheme._();

  // Brand — đồng bộ với admin-dashboard (Indigo primary + Cyan accent)
  static const brandIndigo = Color(0xFF4F46E5);
  static const brandCyan = Color(0xFF06B6D4);

  // Light surfaces
  static const _lightBg = Color(0xFFF8FAFC); // slate-50
  // Dark surfaces (slate-900 / slate-800)
  static const _darkBg = Color(0xFF0F172A);
  static const _darkSurface = Color(0xFF1E293B);

  /// Gradient thương hiệu — dùng cho logo, header, thanh tiến trình.
  static const brandGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [brandIndigo, brandCyan],
  );

  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(seedColor: brandIndigo).copyWith(
      primary: brandIndigo,
      tertiary: brandCyan,
      surface: Colors.white,
    );
    return _build(
      scheme: scheme,
      scaffoldBg: _lightBg,
      surface: Colors.white,
    );
  }

  static ThemeData dark() {
    // fromSeed(dark) tự chọn primary/onPrimary tương phản tốt; chỉ ghim brand cyan làm tertiary.
    final scheme = ColorScheme.fromSeed(
      seedColor: brandIndigo,
      brightness: Brightness.dark,
    ).copyWith(
      tertiary: brandCyan,
      surface: _darkSurface,
    );
    return _build(
      scheme: scheme,
      scaffoldBg: _darkBg,
      surface: _darkSurface,
    );
  }

  /// Builder dùng chung cho cả light & dark — màu lấy từ [scheme]/[surface],
  /// không hardcode trắng/đen để chữ luôn tương phản với nền theo từng chế độ.
  static ThemeData _build({
    required ColorScheme scheme,
    required Color scaffoldBg,
    required Color surface,
  }) {
    return ThemeData(
      colorScheme: scheme,
      useMaterial3: true,
      scaffoldBackgroundColor: scaffoldBg,
      appBarTheme: AppBarTheme(
        backgroundColor: surface,
        foregroundColor: scheme.onSurface,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: TextStyle(
          color: scheme.onSurface,
          fontSize: 18,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: surface,
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: scheme.outlineVariant.withValues(alpha: 0.6)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.primary, width: 1.6),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(50),
          textStyle: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w600,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      navigationDrawerTheme: NavigationDrawerThemeData(
        backgroundColor: surface,
        indicatorColor: scheme.primary.withValues(alpha: 0.10),
        indicatorShape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      listTileTheme: ListTileThemeData(
        selectedColor: scheme.primary,
        selectedTileColor: scheme.primary.withValues(alpha: 0.08),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16),
      ),
      dividerTheme: const DividerThemeData(thickness: 1, space: 1),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        side: BorderSide(color: scheme.outlineVariant),
        backgroundColor: surface,
        selectedColor: scheme.primary.withValues(alpha: 0.12),
        checkmarkColor: scheme.primary,
        labelStyle: const TextStyle(fontWeight: FontWeight.w500),
        showCheckmark: true,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );
  }
}
