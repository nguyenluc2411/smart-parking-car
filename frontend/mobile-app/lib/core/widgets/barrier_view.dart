import 'package:flutter/material.dart';

/// Minh họa barie đóng/mở để quan sát trực quan khi CHƯA có camera thật.
/// Cánh barie nâng lên (mở) / hạ ngang chắn đường (đóng) với animation mượt.
/// [open] = true khi gate.status == 'OPEN'. Cập nhật real-time nhờ provider poll.
class BarrierView extends StatelessWidget {
  const BarrierView({super.key, required this.open, this.direction});

  /// Cánh đang nâng (mở) hay hạ (đóng).
  final bool open;

  /// 'IN' | 'OUT' — chỉ để dán nhãn hướng cho dễ quan sát.
  final String? direction;

  static const _armClosed = 'Đang chắn';
  static const _armOpen = 'Đang mở';

  @override
  Widget build(BuildContext context) {
    final accent = open ? Colors.green.shade600 : Colors.red.shade600;
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Container(
        height: 140,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFEAF1F8), Color(0xFFDDE7F0)],
          ),
        ),
        child: Stack(
          children: [
            // Mặt đường.
            Align(
              alignment: Alignment.bottomCenter,
              child: Container(
                height: 48,
                color: const Color(0xFF455A64),
                child: const _LaneDashes(),
              ),
            ),
            // Ô tô tiến vào (phía bên trái cánh).
            const Positioned(
              left: 18,
              bottom: 8,
              child: Icon(Icons.directions_car, size: 40, color: Colors.white),
            ),
            // Trụ barie + đế (cố định ở bên phải).
            Positioned(
              right: 64,
              bottom: 48,
              child: Container(
                width: 12,
                height: 64,
                decoration: BoxDecoration(
                  color: Colors.blueGrey.shade400,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Hộp điều khiển dưới trụ.
            Positioned(
              right: 58,
              bottom: 40,
              child: Container(
                width: 24,
                height: 14,
                decoration: BoxDecoration(
                  color: Colors.blueGrey.shade700,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Cánh barie: pivot tại trụ (mép phải), vươn sang trái chắn đường.
            Positioned(
              right: 70,
              bottom: 108,
              child: AnimatedRotation(
                // 0 = nằm ngang (chắn); -0.23 vòng ≈ -83° = nâng gần thẳng đứng.
                turns: open ? -0.23 : 0,
                duration: const Duration(milliseconds: 650),
                curve: Curves.easeInOut,
                alignment: Alignment.centerRight,
                child: const _BarrierArm(),
              ),
            ),
            // Đèn tín hiệu + nhãn trạng thái.
            Positioned(
              top: 10,
              right: 12,
              child: Row(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    width: 12,
                    height: 12,
                    decoration: BoxDecoration(
                      color: accent,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(color: accent.withValues(alpha: 0.6), blurRadius: 8),
                      ],
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    open ? _armOpen : _armClosed,
                    style: TextStyle(
                      color: accent,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            if (direction != null)
              Positioned(
                top: 10,
                left: 12,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.55),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    direction == 'IN' ? 'Cổng VÀO' : 'Cổng RA',
                    style: const TextStyle(color: Colors.white, fontSize: 11),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Thanh chắn sọc đỏ-trắng + đối trọng ở gốc.
class _BarrierArm extends StatelessWidget {
  const _BarrierArm();

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Phần sọc (đầu tự do, bên trái).
        Container(
          width: 96,
          height: 12,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(3),
            gradient: const LinearGradient(
              colors: [
                Colors.red, Colors.white, Colors.red, Colors.white,
                Colors.red, Colors.white, Colors.red,
              ],
              stops: [0, 0.14, 0.28, 0.42, 0.56, 0.7, 1],
            ),
          ),
        ),
        // Đối trọng tại pivot (bên phải, sát trụ).
        Container(
          width: 14,
          height: 16,
          decoration: BoxDecoration(
            color: Colors.blueGrey.shade800,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ],
    );
  }
}

class _LaneDashes extends StatelessWidget {
  const _LaneDashes();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(
          9,
          (_) => Container(
            width: 14,
            height: 3,
            margin: const EdgeInsets.symmetric(horizontal: 6),
            color: Colors.amber.shade200,
          ),
        ),
      ),
    );
  }
}
