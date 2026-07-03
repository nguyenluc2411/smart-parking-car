import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/utils/formatters.dart';

void main() {
  group('Fmt', () {
    test('currency formats VND', () {
      expect(Fmt.currency(50000), contains('50.000'));
      expect(Fmt.currency(null), '—');
    });

    test('duration formats seconds', () {
      expect(Fmt.duration(8100), '2h 15m');
      expect(Fmt.duration(2700), '45m');
      expect(Fmt.duration(null), '—');
    });

    test('percent formats ratio', () {
      expect(Fmt.percent(0.64), '64%');
      expect(Fmt.percent(null), '—');
    });
  });
}
