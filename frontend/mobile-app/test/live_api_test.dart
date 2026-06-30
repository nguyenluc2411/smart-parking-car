@Tags(['live'])
library;

// Integration test gọi BACKEND THẬT qua đúng code path của app
// (DioFactory + AuthInterceptor + repository + model parsing).
//
// KHÔNG chạy trong `flutter test` mặc định (gắn tag 'live'). Chạy riêng:
//   flutter test test/live_api_test.dart --dart-define=USE_MOCK=false
//   (Android emulator: thêm --dart-define=*_API_URL=http://10.0.2.2:80xx)
//
// Yêu cầu: docker compose đang chạy & seed admin/ChangeMe123!.

import 'package:flutter_test/flutter_test.dart';

import 'package:mobile_app/core/network/dio_client.dart';
import 'package:mobile_app/features/admin/data/admin_repository.dart';
import 'package:mobile_app/features/parking/data/parking_repository.dart';

void main() {
  test('login thật + lấy 1 trang dữ liệu (sessions) qua API thật', () async {
    String? token;
    final factory = DioFactory(tokenGetter: () => token);

    // 1) LOGIN — admin-service :8083
    final admin = AdminRepository(factory.admin);
    final auth = await admin.login(username: 'admin', password: 'ChangeMe123!');
    expect(auth.accessToken, isNotEmpty);
    expect(auth.role, 'ADMIN');
    token = auth.accessToken; // các call sau tự gắn Bearer qua AuthInterceptor
    // ignore: avoid_print
    print('LOGIN OK  role=${auth.role}  tokenLen=${auth.accessToken.length}');

    // 2) DATA PAGE — parking-service :8081 /sessions
    final parking = ParkingRepository(factory.parking);
    final pageData = await parking.getSessions(page: 0, size: 5);
    expect(pageData.totalElements, greaterThanOrEqualTo(0));
    expect(pageData.content.length, lessThanOrEqualTo(5));
    // ignore: avoid_print
    print('SESSIONS OK  totalElements=${pageData.totalElements}  '
        'returned=${pageData.content.length}');

    // 3) Thêm 1 endpoint khác để chắc parse model ổn — slot availability
    final av = await parking.getAvailability();
    expect(av.totalSlots, greaterThanOrEqualTo(0));
    // ignore: avoid_print
    print('AVAILABILITY OK  total=${av.totalSlots}  empty=${av.emptySlots}');
  });
}
