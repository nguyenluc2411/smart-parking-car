# Business Rules – Smart Parking System

> KHÔNG tạo business rule mới ngoài tài liệu này.
> Mọi rule phải có Rule ID để trace.

---

## BR-001: Nhận diện biển số

- **BR-001-1:** Chỉ xử lý ô tô. Xe máy bị bỏ qua hoàn toàn (filter ở YOLO stage 1).
- **BR-001-2:** Confidence threshold tối thiểu: 0.85. Dưới ngưỡng này → retry tối đa 3 lần trong 5 giây → nếu vẫn thất bại → ghi log, alert operator.
- **BR-001-3:** Định dạng biển số VN hợp lệ: 2 số + **1–2 chữ series** (gồm `Đ`; phủ `51F`, `51LD`, `51MK`, xe rơ-moóc/doanh nghiệp…) + `-` + 3–5 số + tùy chọn `.NN`. Regex: `^[0-9]{2}[A-ZĐ]{1,2}-[0-9]{3,5}(\.[0-9]{2})?$`. Ví dụ: `51F-12345`, `51F-123.45`, `51LD-123.45`.
- **BR-001-5 (Bypass thủ công):** Biển không khớp regex (NG/NN ngoại giao, xe nước ngoài, biển hỏng) hoặc ALPR đọc lỗi → Operator dùng `POST /api/v1/sessions/manual-entry` (vào) và `/manual-exit` (ra) để cho qua. Bỏ qua kiểm tra định dạng (operator xác nhận) nhưng vẫn áp BR-002 (blacklist/duplicate/đầy bãi). Mọi thao tác ghi `triggered_by = OPERATOR:{id}` trong `gate_logs`.
- **BR-001-4:** Biển số được normalize trước khi lưu: uppercase, bỏ khoảng trắng.

## BR-002: Kiểm soát vào bãi

- **BR-002-1:** Xe có biển số trong **blacklist** → từ chối tuyệt đối, không mở barie, ghi log.
- **BR-002-2:** Xe có biển số trong **whitelist** → ưu tiên mở barie ngay, không cần thanh toán trước.
- **BR-002-3:** Xe thường → mở barie bình thường, tạo session.
- **BR-002-4:** Một biển số chỉ được có **1 session ACTIVE** tại một thời điểm. Nếu xe đã trong bãi mà trigger lại → từ chối, không tạo session mới.
- **BR-002-5:** Nếu bãi đầy (slots_occupied >= total_slots) → từ chối xe mới, hiển thị "FULL" trên dashboard.

## BR-003: Quản lý slot

- **BR-003-1:** Tổng số slot cố định, được cấu hình trong `system_config.total_slots`.
- **BR-003-2:** Slot chuyển trạng thái: `EMPTY` → `OCCUPIED` khi xe vào, `OCCUPIED` → `EMPTY` khi xe ra.
- **BR-003-3:** Slot `MAINTENANCE` không được assign cho session mới.
- **BR-003-4 (Chống trôi dạt — Resync):** Admin có thể đồng bộ lại slot bằng `POST /api/v1/slots/resync`. Ground truth = các session `ACTIVE`: slot của mỗi session ACTIVE → `OCCUPIED`; slot không gắn session ACTIVE (và không `MAINTENANCE`) → `EMPTY`. Dùng khi đếm bị lệch do sự cố vận hành.
- **BR-003-5 (Buffer):** Ngưỡng "bãi đầy" trừ hao một tỉ lệ slot (`capacity_buffer_percent`, khuyến nghị ~5%, mặc định 0) để bù sai số khi không có cảm biến thực: từ chối xe mới khi `occupied ≥ total − ceil(total × buffer%)`. Lý tưởng nên dựa trên cảm biến thực tế nếu có.

## BR-004: Tính phí

> **Phương pháp chuẩn: SPLIT BLOCK.** Bóc tách session thành các đoạn theo khung giờ và tính
> mỗi đoạn theo đơn giá tương ứng (KHÔNG dùng start-time base, KHÔNG áp 1 hệ số cho toàn bộ).

- **BR-004-1 (Block 30 phút):** Phí = Σ phí của từng **block 30 phút**, mỗi block tính theo **khung giờ tại thời điểm block BẮT ĐẦU** (không trộn 2 giá trong cùng một block — hóa đơn dễ giải thích):
  - **Giờ thường:** block = `30 × rate_per_min`.
  - **Giờ cao điểm** (07:00–09:00 và 17:00–19:00): block = `30 × rate_per_min × peak_multiplier` (mặc định 1.5x) nếu block bắt đầu trong khung peak.
  - **Khung qua đêm** (22:00–06:00): block bắt đầu trong khung này là *block đêm* → xem BR-004-3.
- **BR-004-2:** Thời lượng gửi được chia thành **block 30 phút**, block cuối (lẻ) **làm tròn LÊN** đủ 30 phút. Giá mỗi block do khung-giờ-bắt-đầu quyết định (vd 31 phút giờ peak = 2 block peak = 60′ peak, KHÔNG còn cảnh "31′ peak + 29′ thường").
- **BR-004-3 + ân hạn (grace):** `overnight_flat` là phí khoán **mỗi đêm**, **chỉ áp khi** tổng thời gian của đêm đó trong khung 22:00–06:00 **vượt `overnight-grace-minutes` (mặc định 60 phút)**. Nếu **≤ 60 phút**, các block đêm đó tính theo **giá thường** (không chém gói qua đêm cho vài phút lố). Gửi nhiều đêm → mỗi đêm vượt grace cộng 1 flat.
  - Ví dụ grace: vào 21:58 ra 22:02 = 1 block bắt đầu lúc 21:58 (giờ thường) → 1 block thường, KHÔNG có phí đêm.
  - **Ví dụ A** (16:30→19:30): 30′ thường (16:30–17:00) + 120′ peak (17:00–19:00) + 30′ thường (19:00–19:30).
  - **Ví dụ B** (21:00→07:00): 60′ thường (21:00–22:00) + `overnight_flat` (22:00–06:00) + 60′ thường (06:00–07:00).
- **BR-004-4:** Phí tối thiểu: `min_charge` VNĐ (mặc định 5.000đ), áp cho *tổng* phí mỗi phiên sau khi cộng các đoạn.
- **BR-004-5:** Phí tính tại thời điểm xe ra. Không tính phí trước.
- **BR-004-6:** 1 session chỉ có đúng 1 invoice (idempotency constraint trên session_id).
- **BR-004-7:** Khung **peak** đọc từ bảng `rate_schedules` (cấu hình động), không hardcode — mỗi dòng có `hour_start`/`hour_end` + `day_type` (`ALL` | `WEEKDAY` | `WEEKEND`). Khung **overnight** (22:00–06:00) là khung của `overnight_flat` trên bảng `rates`.

## BR-005: Thanh toán

- **BR-005-1:** Phương thức được phép: CASH, QR_CODE.
- **BR-005-2 (Pay-before-exit — Auto-pay QR · ĐÃ TRIỂN KHAI):** Cổng ra **chỉ mở sau khi thanh toán**. Xe vãng lai khi quét RA: parking phát `session.closed` (billing tạo hóa đơn `PENDING`) nhưng **giữ barie ĐÓNG**; khách quét **QR MoMo** trả tiền → billing phát `billing.payment.completed` → **parking nghe event này và mở barie cổng ra** (`parking.gate.command` OPEN). Chạy **24/7 kể cả ban đêm** (QR không cần nhân viên), đóng hoàn toàn lỗ hổng thu tiền ngoài giờ.
  - *Trước đây (Phase 1, đã thay):* barie mở ngay rồi đối soát sau — bỏ vì xe vãng lai có thể đạp ga chạy trốn vé.
- **BR-005-3:** Khách tự thanh toán QR (MoMo). Operator vẫn có thể xác nhận tay qua dashboard cho ca ngoại lệ.
- **BR-005-4:** Whitelist vehicles: cổng ra **mở ngay** (không chờ thanh toán), invoice được đánh dấu WAIVED.
- **BR-005-5 (Barie gated theo thanh toán):** Với xe **vãng lai**, barie cổng ra **giữ đóng cho tới khi `payment.completed`**. Ngoại lệ mở ngay: **whitelist** (miễn phí) và **manual-exit của operator** (người duyệt). Một thao tác mở cổng do operator luôn được tôn trọng.
- **BR-005-6 (Ngoài giờ vận hành):** Trong khung 22:00–06:00 vẫn **bắt thanh toán QR như ban ngày** (không còn "ra tự do ngoài giờ"). QR MoMo 24/7 nên xe vãng lai vẫn trả được không cần nhân viên → **không còn thất thu ngoài giờ**. Phí ngoài giờ theo `overnight_flat` (BR-004-3).
- **BR-005-8 (Báo cáo doanh thu theo phương thức — yêu cầu giảng viên):** Báo cáo doanh thu
  (`GET /billing/report/daily`, `/monthly`) phải tách được **tiền mặt** (`CASH`) và **tiền hệ thống**
  (`QR_CODE` + `ONLINE`, gộp lại vì cùng là tiền không qua tay operator trực tiếp) — trường
  `revenueByMethod`, gộp theo `payments.method` cho các invoice trong kỳ. Hóa đơn `WAIVED`
  (whitelist) không có `payment` nên không xuất hiện trong breakdown này.
- **BR-005-7 (Loại trừ giữa các kênh thanh toán):** Một invoice chỉ được thanh toán qua **đúng 1 kênh**. Khi invoice được settle qua bất kỳ kênh nào (operator xác nhận CASH/QR_CODE qua dashboard, driver tự trả qua app, hoặc MoMo báo thanh toán thành công), billing-service **hủy PayOS payment link còn đang PENDING của invoice đó** (nếu có) qua PayOS API để khách không thể quét QR PayOS trả tiếp trên một invoice đã thu tiền. MoMo không có API hủy public — trường hợp PayOS trả trước, lệnh MoMo cũ chỉ được vô hiệu hoá gián tiếp qua guard idempotent trên invoice status (không tạo thanh toán trùng trong hệ thống, nhưng không hủy được ở phía cổng MoMo). Hệ quả UI: một khi operator chọn xác nhận **tiền mặt**, client hiển thị QR cho khách (kiosk cổng ra) phải **dừng tạo/hiển thị QR mới** cho invoice đó; ngược lại một khi khách đã chọn thanh toán QR, thao tác xác nhận tiền mặt cho invoice đó ở dashboard sẽ bị từ chối (invoice không còn ở trạng thái PENDING).

## BR-006: Điều khiển barie

- **BR-006-1:** Barie chỉ có 2 trạng thái: OPEN và CLOSED. Không có trạng thái trung gian.
- **BR-006-2:** Sau khi OPEN, barie tự CLOSE sau 10 giây (hoặc khi cảm biến xác nhận xe đã qua).
- **BR-006-3:** Timeout 10 giây: nếu không nhận được xác nhận xe đã qua → **KHÔNG hard-cancel** session (tránh "xe ma"); chuyển sang `REQUIRES_ATTENTION` để Operator kiểm tra camera và chốt tay (xem BR-006-5).
- **BR-006-4:** Override thủ công chỉ ADMIN mới được làm. Phải ghi log với lý do.
- **BR-006-5 (Đối soát thủ công — REQUIRES_ATTENTION):** Mọi tình huống không chắc chắn (entry timeout, **xe ra không khớp session ACTIVE**) → session đánh dấu `REQUIRES_ATTENTION` thay vì hủy/từ chối.
  - Xe ra không có session ACTIVE → luôn tạo session `REQUIRES_ATTENTION` (entry chưa rõ, là placeholder) + cảnh báo operator. KHÔNG phát `session.closed` (không có thời lượng tin cậy để tính phí). Hành vi **barie** do `app.parking.exit-policy` quyết định:
    - `require-match` (**mặc định**): **giữ barie ĐÓNG** + alert `UNMATCHED_EXIT` mức **CRITICAL**; operator đối soát camera rồi mở bằng gate override / `manual-exit`. Vá lỗ hổng biển giả lái xe trộm ra; an toàn khi demo.
    - `open-always`: **mở barie ngay** (không giam xe, BR-005-6) + alert mức WARNING — dùng cho bãi không người trực (OCR chưa hoàn hảo có thể đọc sai biển lúc VÀO → nếu giữ đóng sẽ nhốt nhầm khách thật), nhưng chấp nhận rủi ro trộm/thất thu.
  - Operator chốt qua `POST /api/v1/sessions/{id}/resolve` → `CLOSED` hoặc `CANCELLED` sau khi kiểm tra camera. Liệt kê việc cần xử lý: `GET /api/v1/sessions?status=REQUIRES_ATTENTION`.
  - **Chống quét trùng (idempotency):** một lần quét RA lặp lại của chính xe vừa ra (Kafka redelivery, burst nhiều khung, trigger lại) — trong vòng `app.parking.exit-dedup-seconds` (mặc định 30s) kể từ lần ra trước (session `CLOSED`/`REQUIRES_ATTENTION`) — bị **bỏ qua**: KHÔNG tạo session `REQUIRES_ATTENTION` ma, KHÔNG mở lại barie. (Quét VÀO trùng đã được BR-002-4 chặn: 1 session ACTIVE/biển.)

## BR-007: Alerts & Monitoring

- **BR-007-1:** Alert CRITICAL khi: bất kỳ service DOWN > 1 phút, DLT nhận message.
- **BR-007-2:** Alert WARNING khi: ALPR failure rate > 30% trong 5 phút, Kafka consumer lag > 100, ALPR p95 latency > 2 giây.
- **BR-007-3:** Audit log bắt buộc cho: login/logout, override barie, thay đổi whitelist, thay đổi bảng giá, tạo/xóa user.
- **BR-007-4 (Cảnh báo nghiệp vụ/an ninh — real-time):** parking-service phát cảnh báo và đẩy **real-time qua SSE** (`GET /api/v1/alerts/stream`) cho OPERATOR/ADMIN, kèm ảnh khung hình để đối soát biển giả. Lưu bảng `alerts`, operator xác nhận qua `POST /api/v1/alerts/{id}/ack`. Các loại:
  - `DUPLICATE_ACTIVE_ENTRY` (**CRITICAL**): biển đang có session ACTIVE lại quét VÀO (BR-002-4) — dấu hiệu **biển giả/clone**.
  - `BLACKLIST_HIT` (**CRITICAL**): xe blacklist bị từ chối vào (BR-002-1).
  - `UNMATCHED_EXIT`: xe ra không khớp session ACTIVE (BR-006-5). Mức **CRITICAL** khi `exit-policy=require-match` (barie giữ đóng, cần operator mở), **WARNING** khi `open-always` (đã tự mở).
  - `LOW_CONFIDENCE` (**WARNING**): ALPR đọc dưới ngưỡng (BR-001-2).
  - *Khác BR-007-1/2 (hạ tầng: service down, DLT, lag — qua Prometheus/Grafana).*

## BR-008: Giờ vận hành

- **BR-008-1:** Hệ thống hoạt động 06:00–22:00 hàng ngày.
- **BR-008-2:** Ngoài giờ vận hành: từ chối xe mới vào, cho phép xe trong bãi ra tự do (phí overnight_flat).
- **BR-008-3:** Giờ vận hành có thể cấu hình qua `system_config` bởi ADMIN.
