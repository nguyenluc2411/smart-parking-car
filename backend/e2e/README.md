# E2E — kiểm thử trên stack thật

Các script ở đây chạy **qua HTTP vào stack đang chạy thật** (Postgres thật, Kafka thật), phủ đúng
những thứ unit test không chạm tới được:

- Flyway migrate sạch trên Postgres thật (không phải H2 / mock).
- Luồng đi qua nhiều service: đặt chỗ (parking) → xe vào → hóa đơn qua Kafka (billing).
- Các ràng buộc nằm ở **tầng DB** (partial unique index, `SELECT … FOR UPDATE`) — mock không có.

> Chúng đã bắt được 3 lỗi mà unit test để lọt, trong đó có một migration billing **gãy trên DB thật**
> vì dữ liệu sẵn có vi phạm ràng buộc mới. Xem `PROJECT_CONTEXT.md`.

## Chạy

```bash
docker compose up -d --build          # ở thư mục gốc repo
python backend/e2e/run_all.py         # hoặc từng file: python backend/e2e/test_billing.py
```

Chỉ dùng thư viện chuẩn của Python — không cần `pip install` gì cả. Cần Python 3.10+ và `docker`
trong PATH (script đọc mã OTP từ log của `admin-service`).

Thoát khác 0 nếu có check nào fail, nên gắn vào CI được.

## Cấu hình

| Biến môi trường | Mặc định |
|---|---|
| `E2E_ADMIN_URL` / `E2E_PARKING_URL` / `E2E_BILLING_URL` | `http://localhost:8083` / `:8081` / `:8082` |
| `E2E_ADMIN_USER` / `E2E_ADMIN_PASSWORD` | `admin` / `ChangeMe123!` (seed của V1) |
| `E2E_DRIVER_PHONE` | `0901234567` |

## Nội dung từng file

| File | Phủ |
|---|---|
| `test_reservations.py` | BR-009 đặt chỗ + BR-003-6 tọa độ lưới + BR-003-4b (resync không giải phóng slot đang giữ) |
| `test_slot_guards.py` | BR-009-3b — admin không sửa/xóa/thu-nhỏ-zone được slot đang có lượt đặt |
| `test_billing.py` | BR-005-8 `collected`, BR-004 breakdown hóa đơn, BR-005-7 thu tiền mặt lúc mất điện, BR-005-2 chặn thu hai lần |

## Lưu ý khi viết thêm

- **Mỗi lần chạy dùng biển số mới** (`fresh_plate()`). Biển cố định sẽ thừa hưởng trạng thái lần chạy
  trước — xe còn trong bãi, biển đã được duyệt — và script chết ở khâu dựng dữ liệu chứ không phải ở
  thứ nó định kiểm tra. Đây là lỗi đã thực sự xảy ra.
- **Dọn dẹp sau khi chạy** (cho xe ra, hủy lượt đặt). Bãi demo từng đầy 11/12 slot vì các lần test cũ
  để xe lại, khiến lần chạy sau fail vì "hết chỗ".
- **Không đóng session của người khác.** Bãi dev có cả dữ liệu demo của đồng đội; cần chỗ trống thì
  `provision` thêm slot, đừng cho xe của họ ra.
- OTP đọc từ log `admin-service` vì `LoggingOtpSender` chỉ ghi log chứ không gửi SMS. Khi nào thay
  bằng SMS gateway thật thì `otp_from_logs()` phải đổi theo.
