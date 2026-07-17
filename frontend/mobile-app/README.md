# Smart Parking — Mobile (Flutter)

Ứng dụng mobile cho hệ thống Smart Parking. Một codebase, **2 flavor**:

- **operator** (Phase 1 — đã có): cho nhân viên vận hành / quản trị. Dùng REST API
  hiện có trong `../../docs/api-contracts.md`. Không cần sửa backend.
- **driver** (Phase 2 — placeholder): cho tài xế. Cần backend mở rộng (tài khoản
  tài xế, auth riêng, endpoint `my-sessions/my-invoices/pay`) — xem `ADR-009`.

> Stack: Flutter + Riverpod (state) + dio (HTTP) + go_router (điều hướng) + fl_chart.
> Mock: dio interceptor đọc `assets/mock/api-mock.json` (bản sao của `../mock/api-mock.json`).

---

## Chạy app

### Flavor operator với mock (không cần backend)
```bash
flutter run -t lib/main_operator.dart --dart-define=USE_MOCK=true
```
Đăng nhập mock:
- `admin` / `secret` → vai trò **ADMIN** (thấy đủ menu)
- `operator01` / `secret` → vai trò **OPERATOR** (ẩn các trang ADMIN-only)

### Flavor operator với backend thật
```bash
flutter run -t lib/main_operator.dart \
  --dart-define=USE_MOCK=false \
  --dart-define=PARKING_API_URL=http://10.0.2.2:8081 \
  --dart-define=BILLING_API_URL=http://10.0.2.2:8082 \
  --dart-define=ADMIN_API_URL=http://10.0.2.2:8083
```
> Android emulator: host máy thật là `10.0.2.2` (không phải `localhost`).

### Flavor driver (placeholder Phase 2)
```bash
flutter run -t lib/main_driver.dart
```

`flutter run` không kèm `-t` sẽ mặc định chạy flavor **operator**.

---

## Cấu trúc (clean architecture)

```
lib/
├── main.dart / main_operator.dart / main_driver.dart   # entrypoints theo flavor
├── app/                # flavor, app widget, router (guard), home shell, DI providers
├── core/               # env config, network (dio, interceptors, mock), storage, theme, widgets, utils
└── features/
    ├── auth/           # login + auth controller (Riverpod)
    ├── dashboard/      # tổng quan: slot, doanh thu, phiên active
    ├── sessions/       # danh sách + chi tiết phiên
    ├── gates/          # trạng thái cổng + override (ADMIN)
    ├── billing/        # hóa đơn, thanh toán, bảng giá (ADMIN)
    ├── reports/        # báo cáo ngày/tháng (ADMIN) — fl_chart
    ├── vehicles/       # whitelist (ADMIN)
    ├── users/          # người dùng (ADMIN)
    └── driver/         # app tài xế (Phase 2 placeholder)
```

**Nguyên tắc:** UI không gọi API trực tiếp → qua Riverpod provider → repository →
dio. Model khớp 100% `docs/api-contracts.md`. Không dùng `any`/`dynamic` tùy tiện.

---

## Phân quyền (role)

Trang ADMIN-only: `/reports`, `/rates`, `/vehicles`, `/users` — bị router chặn với
OPERATOR và ẩn khỏi Drawer. Nút override cổng chỉ hiện với ADMIN.

---

## Kiểm thử

```bash
flutter analyze        # 0 issue
flutter test           # unit test formatters
```
