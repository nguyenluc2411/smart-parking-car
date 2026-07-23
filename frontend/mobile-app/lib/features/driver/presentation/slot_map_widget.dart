import 'package:flutter/material.dart';

/// Sơ đồ MÔ PHỎNG vị trí slot trong zone (BR-003-6), dựng từ `gridRow`/`gridCol`
/// trả về khi đặt chỗ. Tài xế (role DRIVER) không có quyền đọc toàn bộ danh sách
/// slot của bãi (chỉ operator/admin mới gọi được `/api/v1/slots`), nên đây KHÔNG
/// phải bản đồ real-time toàn bãi — chỉ là lưới minh họa để tài xế hình dung
/// nhanh ô của mình nằm ở đâu trong zone, không vẽ được các ô khác đang trống/đầy.
class SlotMapWidget extends StatelessWidget {
  const SlotMapWidget({
    super.key,
    required this.zone,
    required this.slotCode,
    required this.gridRow,
    required this.gridCol,
  });

  final String zone;
  final String slotCode;
  final int? gridRow;
  final int? gridCol;

  // Quy ước seed dữ liệu: 10 ô/hàng trong một zone (xem V6__add_slot_grid_coordinates.sql).
  static const _cols = 10;

  @override
  Widget build(BuildContext context) {
    final row = gridRow;
    final col = gridCol;
    if (row == null || col == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(Icons.map_outlined,
                  color: Theme.of(context).colorScheme.outline),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Chưa có sơ đồ cho ô $slotCode (zone $zone) — vẫn giữ chỗ '
                  'bình thường, chỉ là chưa vẽ được vị trí.',
                  style: TextStyle(color: Theme.of(context).colorScheme.outline),
                ),
              ),
            ],
          ),
        ),
      );
    }

    final rows = row + 2; // đủ để ô của mình không nằm sát mép dưới
    final theme = Theme.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.event_seat, color: theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text('Sơ đồ minh họa · Zone $zone',
                    style: const TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 8),
            AspectRatio(
              aspectRatio: _cols / rows,
              child: GridView.builder(
                physics: const NeverScrollableScrollPhysics(),
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: _cols,
                  crossAxisSpacing: 3,
                  mainAxisSpacing: 3,
                ),
                itemCount: _cols * rows,
                itemBuilder: (context, i) {
                  final r = i ~/ _cols;
                  final c = i % _cols;
                  final isMine = r == row && c == col;
                  return Container(
                    decoration: BoxDecoration(
                      color: isMine
                          ? theme.colorScheme.primary
                          : theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(
                        color: theme.colorScheme.outlineVariant,
                        width: 0.5,
                      ),
                    ),
                    child: isMine
                        ? Icon(Icons.directions_car,
                            size: 14, color: theme.colorScheme.onPrimary)
                        : null,
                  );
                },
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Ô của bạn: $slotCode — sơ đồ mang tính minh họa, không hiển thị '
              'các ô khác đang trống hay đầy.',
              style: theme.textTheme.bodySmall
                  ?.copyWith(color: theme.colorScheme.outline),
            ),
          ],
        ),
      ),
    );
  }
}
