import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// Logo thương hiệu Smart Parking — ALPR.
///
/// Vẽ bằng [CustomPainter] (không phụ thuộc asset/SVG), khớp 1-1 với logo
/// web `admin-dashboard` (`BrandLogo.tsx` / `icon.svg`): chữ "P" nét đều
/// (Parking) + khung quét góc gợi ALPR + chấm cảm biến ("smart").
///
/// - [BrandLogo] (mặc định): tile gradient Indigo→Cyan tự chứa.
/// - [BrandLogo.mono]: chỉ glyph trắng, nền trong suốt — đặt trên bề mặt
///   đã mang màu thương hiệu (vd. header gradient, tile frosted).
class BrandLogo extends StatelessWidget {
  const BrandLogo({super.key, this.size = 40})
      : _mono = false,
        _glyphColor = Colors.white;

  /// Chỉ vẽ glyph (mặc định màu trắng), không có tile nền.
  const BrandLogo.mono({super.key, this.size = 40, Color color = Colors.white})
      : _mono = true,
        _glyphColor = color;

  final double size;
  final bool _mono;
  final Color _glyphColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _BrandLogoPainter(mono: _mono, glyphColor: _glyphColor),
      ),
    );
  }
}

class _BrandLogoPainter extends CustomPainter {
  _BrandLogoPainter({required this.mono, required this.glyphColor});

  final bool mono;
  final Color glyphColor;

  @override
  void paint(Canvas canvas, Size size) {
    // Toàn bộ hình học định nghĩa trong không gian 48x48 (như viewBox SVG).
    final scale = size.width / 48.0;
    canvas.save();
    canvas.scale(scale);

    if (!mono) {
      final tileRect = RRect.fromRectAndRadius(
        const Rect.fromLTWH(0, 0, 48, 48),
        const Radius.circular(13),
      );
      final tilePaint = Paint()
        ..shader = AppTheme.brandGradient.createShader(
          const Rect.fromLTWH(0, 0, 48, 48),
        );
      canvas.drawRRect(tileRect, tilePaint);
    }

    // Khung quét ALPR — góc trên-trái + dưới-phải.
    final bracketPaint = Paint()
      ..style = PaintingStyle.stroke
      ..color = glyphColor.withValues(alpha: 0.55)
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final brackets = Path()
      // M11 16.5 V13 a2 2 0 0 1 2 -2 H16.5
      ..moveTo(11, 16.5)
      ..lineTo(11, 13)
      ..arcToPoint(const Offset(13, 11),
          radius: const Radius.circular(2), clockwise: true)
      ..lineTo(16.5, 11)
      // M37 31.5 V35 a2 2 0 0 1 -2 2 H31.5
      ..moveTo(37, 31.5)
      ..lineTo(37, 35)
      ..arcToPoint(const Offset(35, 37),
          radius: const Radius.circular(2), clockwise: true)
      ..lineTo(31.5, 37);
    canvas.drawPath(brackets, bracketPaint);

    // Chữ "P" — Parking.
    final pPaint = Paint()
      ..style = PaintingStyle.stroke
      ..color = glyphColor
      ..strokeWidth = 4.6
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final p = Path()
      // M17.5 35 V14 H25 a6.5 6.5 0 0 1 0 13 H17.5
      ..moveTo(17.5, 35)
      ..lineTo(17.5, 14)
      ..lineTo(25, 14)
      ..arcToPoint(const Offset(25, 27),
          radius: const Radius.circular(6.5), clockwise: true)
      ..lineTo(17.5, 27);
    canvas.drawPath(p, pPaint);

    // Chấm cảm biến — "smart" / điểm bắt biển số.
    final dotPaint = Paint()
      ..style = PaintingStyle.fill
      ..color = glyphColor;
    canvas.drawCircle(const Offset(30.5, 32), 2.4, dotPaint);

    canvas.restore();
  }

  @override
  bool shouldRepaint(_BrandLogoPainter old) =>
      old.mono != mono || old.glyphColor != glyphColor;
}
