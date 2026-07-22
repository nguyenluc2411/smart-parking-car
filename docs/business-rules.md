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
- **BR-001-6 (Camera toàn cảnh — ảnh đối chứng):** Mỗi cổng có thể gắn **camera thứ hai** (`auto_capture_{entry,exit}_overview_source`). Camera *biển số* đặt thấp, zoom sát — và **chỉ** nó chạy OCR. Camera *toàn cảnh* đặt cao, lấy trọn chiếc xe; nó **không bao giờ** đưa vào ALPR, chỉ lưu một khung hình cạnh ảnh biển (`..._xxx.overview.jpg`).
  - Lý do: camera biển số một mình **không thể** trả lời "chiếc xe trước barie có đúng là xe của biển này không" — đó chính là thứ mà **biển giả/clone** đang khai thác (BR-007-4 `DUPLICATE_ACTIVE_ENTRY`). Ảnh toàn cảnh cho operator đối chứng loại xe/màu xe khi soi cảnh báo.
  - Khung toàn cảnh chụp **sau** burst ALPR, không phải trong lúc chạy: nó là **bằng chứng**, không phải đầu vào của quyết định. Chặn luồng ALPR để chờ camera thứ hai sẽ khiến việc đọc biển phụ thuộc vào một camera không có tiếng nói gì trong đó.
  - Không có camera thứ hai (hoặc nó không trả khung hình) → **ghi log rồi đi tiếp**, chỉ lưu ảnh biển. Mất ảnh đối chứng không bao giờ được phép làm mất luôn lượt nhận diện.

## BR-002: Kiểm soát vào bãi

- **BR-002-1:** Xe có biển số trong **blacklist** → từ chối tuyệt đối, không mở barie, ghi log.
- **BR-002-2:** Xe có biển số trong **whitelist** → ưu tiên mở barie ngay, không cần thanh toán trước.
- **BR-002-3:** Xe thường → mở barie bình thường, tạo session.
- **BR-002-4:** Một biển số chỉ được có **1 session ACTIVE** tại một thời điểm. Nếu xe đã trong bãi mà trigger lại → từ chối, không tạo session mới.
- **BR-002-5:** Nếu bãi đầy (slots_occupied >= total_slots) → từ chối xe mới, hiển thị "FULL" trên dashboard.

## BR-003: Quản lý slot

- **BR-003-1:** Tổng số slot cố định, được cấu hình trong `system_config.total_slots`.
- **BR-003-2:** Slot chuyển trạng thái: `EMPTY` → `OCCUPIED` khi xe vào, `OCCUPIED` → `EMPTY` khi xe ra. Với xe có đặt chỗ (BR-009): `EMPTY` → `RESERVED` khi đặt → `OCCUPIED` khi xe vào; hoặc `RESERVED` → `EMPTY` khi hủy/hết hạn giữ.
- **BR-003-3:** Slot `MAINTENANCE` không được assign cho session mới.
- **BR-003-4 (Chống trôi dạt — Resync):** Admin có thể đồng bộ lại slot bằng `POST /api/v1/slots/resync`. Ground truth = các session `ACTIVE`: slot của mỗi session ACTIVE → `OCCUPIED`; slot không gắn session ACTIVE (và không `MAINTENANCE`) → `EMPTY`. Dùng khi đếm bị lệch do sự cố vận hành.
- **BR-003-4b (Resync và slot đang được giữ chỗ):** Ground truth "session ACTIVE" **không áp cho slot `RESERVED`** — lượt đặt chỗ chưa có session nào cả (xe chưa tới). Nếu resync cứ theo luật chung, nó sẽ đẩy slot đang giữ về `EMPTY` và trao cho xe vãng lai kế tiếp — đúng cái hỏng mà đặt chỗ sinh ra để tránh. Vì vậy slot có lượt đặt `HELD` được **bỏ qua** như `MAINTENANCE`; nếu cờ trạng thái đã trôi về `EMPTY` thì resync **sửa ngược lại thành `RESERVED`**.
- **BR-003-5 (Buffer):** Ngưỡng "bãi đầy" trừ hao một tỉ lệ slot (`capacity_buffer_percent`, khuyến nghị ~5%, mặc định 0) để bù sai số khi không có cảm biến thực: từ chối xe mới khi `occupied + reserved ≥ total − ceil(total × buffer%)`. Lý tưởng nên dựa trên cảm biến thực tế nếu có.
- **BR-003-6 (Bản đồ bãi — tọa độ lưới):** Mỗi slot có `grid_row`/`grid_col` — vị trí trong lưới của **zone** đó, tính từ góc trên-trái, mặc định 10 slot/hàng. `slot_code` ("A05") nói *slot nào* nhưng không nói *ở đâu*, nên không có tọa độ thì không client nào vẽ được bản đồ chỉ chỗ cho tài xế.
  - **Chỉ là lưới, không phải bản vẽ tỉ lệ:** không mét, không góc xoay. Bản đồ bãi được đọc trong 2 giây khi đang lái; sơ đồ mặt bằng đúng tỉ lệ là bài toán khác và lớn hơn nhiều.
  - Tọa độ do **server gán**, xếp lại (re-flow) theo thứ tự `slot_code` mỗi khi zone thêm/xóa slot — để bản đồ không thủng lỗ sau khi xóa. Ràng buộc `uq_slots_zone_grid` chặn 2 slot cùng một ô.
  - `grid_row`/`grid_col` có thể `null` (slot tạo trước khi có bản đồ): client **bỏ qua** slot chưa có tọa độ, không tự đoán vị trí.

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
  - **Đổi bảng giá lúc xe đang trong bãi:** hóa đơn dùng bảng giá **hiệu lực tại giờ RA**, áp cho
    **toàn bộ** thời gian gửi — kể cả những block trước lúc đổi giá. Đây là **lựa chọn có chủ đích**
    (1 hóa đơn tham chiếu đúng 1 `rate_id`, dễ đối soát), không phải lỗi. Hệ quả: xe vào lúc giá cũ,
    ra sau khi tăng giá sẽ trả **toàn bộ theo giá mới**.
  - Vì vậy **không đổi giá trong giờ cao điểm**; đổi lúc bãi vắng nhất (thường 03:00–05:00) để số xe
    bị ảnh hưởng gần bằng 0. Mọi thay đổi giá đều vào `audit_logs` (BR-007-3).
  - Nếu sau này muốn tính theo giá tại từng block, phải sửa `FeeCalculator` nhận `List<Rate>` thay vì
    1 `Rate`, và hóa đơn phải tham chiếu nhiều `rate_id` — thay đổi lớn, cần ADR riêng.
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
  (`GET /billing/report/daily`, `/monthly`) phải tách được **tiền mặt** (`CASH` + `CASH_OFFLINE`) và
  **tiền hệ thống** (`QR_CODE` + `ONLINE`, gộp lại vì cùng là tiền không qua tay operator trực tiếp)
  — trường `collected` với `cashTotal`/`gatewayTotal`/`byMethod`.
  - Gộp theo **`payments.paid_at`** (giờ thực nhận tiền), **không** theo giờ RA của hóa đơn. Chỉ cách
    neo này mới đối chiếu được với **két tiền cuối ca**: tiền mặt thu lúc mất điện (BR-005-7) nhận
    vào ngày xe ra nhưng nhập máy vài ngày sau, neo theo giờ RA sẽ đẩy tiền vào ca chưa từng cầm nó.
  - Vì vậy `collected.total` **không** bằng `totalRevenue` (đã tính tiền, gồm cả hóa đơn `PENDING`).
    Chênh lệch chính là thứ cần soi — đừng ép hai số bằng nhau.
  - Hóa đơn `WAIVED` (whitelist) không có `payment` nên không xuất hiện trong breakdown này.
- **BR-005-9 (Loại trừ giữa các kênh thanh toán):** Một invoice chỉ được thanh toán qua **đúng 1 kênh**. Khi invoice được settle qua bất kỳ kênh nào (operator xác nhận CASH/QR_CODE qua dashboard, driver tự trả qua app, hoặc MoMo báo thanh toán thành công), billing-service **hủy PayOS payment link còn đang PENDING của invoice đó** (nếu có) qua PayOS API để khách không thể quét QR PayOS trả tiếp trên một invoice đã thu tiền. MoMo không có API hủy public — trường hợp PayOS trả trước, lệnh MoMo cũ chỉ được vô hiệu hoá gián tiếp qua guard idempotent trên invoice status (không tạo thanh toán trùng trong hệ thống, nhưng không hủy được ở phía cổng MoMo). Hệ quả UI: một khi operator chọn xác nhận **tiền mặt**, client hiển thị QR cho khách (kiosk cổng ra) phải **dừng tạo/hiển thị QR mới** cho invoice đó; ngược lại một khi khách đã chọn thanh toán QR, thao tác xác nhận tiền mặt cho invoice đó ở dashboard sẽ bị từ chối (invoice không còn ở trạng thái PENDING).

- **BR-005-7 (Mất điện — thu tiền mặt bù):** Barie là **fail-safe cơ khí**: mất điện → nhả chốt, đẩy tay mở được (yêu cầu an toàn PCCC). Hệ quả: BR-005-5 không còn giữ được xe, nên tiền thu bằng **quy trình giấy + người**, nhập lại sau khi có điện.
  - **Nhân viên đứng tại cổng ra** suốt thời gian sự cố. Điện thoại có 4G → mở trang quét, ALPR + QR vẫn chạy **nếu server còn sống** (server đặt cloud, hoặc tại bãi có UPS). Đây là đường ưu tiên: thu đủ tiền, không cần giấy.
  - Chỉ khi **không với tới server** mới dùng **phiếu giấy 2 liên, số sê-ri in sẵn**: ghi biển số + giờ ra + chữ ký. Cuối ca, số phiếu đã dùng phải khớp tiền mặt nộp — đây là **kiểm soát duy nhất** với tiền thu lúc mất điện.
  - Có điện lại: operator chốt phiên qua `POST /api/v1/sessions/{id}/resolve` rồi ghi nhận tiền qua `POST /api/v1/billing/sessions/{sessionId}/pay` với `method = CASH_OFFLINE`, kèm **bắt buộc** `offlineVoucherNo` (số phiếu) và `paidAt` (giờ thực nhận tiền, không phải giờ nhập máy).
  - `CASH_OFFLINE` vẫn đưa hóa đơn về `PAID` như mọi phương thức khác — **không thêm trạng thái mới**. Nhờ đó guard "chỉ hóa đơn PENDING mới trả được" tự động chặn thu lần hai khi QR đã thành công trước lúc cúp điện.
  - `paidAt` chỉ được chấp nhận với `CASH_OFFLINE`, và phải nằm trong khoảng `[exit_time, hiện tại]`. Các phương thức thu trực tiếp luôn để **server đóng dấu giờ** — nhận `paidAt` từ client sẽ cho phép nhân viên ghi lùi ngày để giấu doanh thu.
  - **Cổng VÀO đóng hoàn toàn** khi mất điện: không camera thì không có `entry_time`, không ảnh, không kiểm tra được blacklist — xe đó lúc ra sẽ thành `UNMATCHED_EXIT` và không thu được tiền. Thà mất khách còn hơn vỡ dữ liệu.
  - Trạng thái `gates.status` trong DB **sẽ sai** trong lúc sự cố (phần mềm ghi CLOSED, thực tế barie mở tay). Đồng bộ lại sau khi có điện, tương tự `POST /api/v1/slots/resync` (BR-003-4).

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

## BR-009: Đặt chỗ trước (tài xế)

> Phạm vi: tài khoản `DRIVER` đặt trước một chỗ trong bãi. Endpoint `/api/v1/driver/reservations`.

- **BR-009-1 (Ai được đặt):** Chỉ đặt bằng **biển đã xác minh** thuộc tài khoản đó (danh sách biển nằm trong JWT). Không có ràng buộc này, một tài khoản có thể giữ chỗ bằng biển người khác — hoặc bằng biển không tồn tại. Đặt trước tối đa `max-lead-hours` (mặc định **72 giờ**); xa hơn thế là giữ chỗ theo phỏng đoán chứ không phải theo kế hoạch. Giờ hẹn trong quá khứ (quá 5 phút) bị từ chối.
- **BR-009-2 (Giữ chỗ và hết hạn):** Slot được giữ từ giờ hẹn đến `hold_until = start_time + hold-minutes` (mặc định **20 phút**). Hết hạn mà xe chưa vào → lượt đặt thành `EXPIRED`, slot trả về pool.
  - Một job quét mỗi `sweep-ms` (mặc định 60s) để **giải phóng** slot hết hạn. Job này là lưới an toàn, **không phải nguồn sự thật**: cổng vào tự coi lượt đặt quá hạn là chết, nên job chạy trễ hay chạy sót cũng không bao giờ cho người bỏ hẹn nhận lại chỗ.
- **BR-009-3 (Giữ chỗ phải là chỗ thật):** Đặt chỗ **lấy một slot thật ra khỏi pool** và chuyển nó sang `RESERVED`. Một lượt đặt không rút slot khỏi pool thì không phải đặt chỗ — tài xế đến nơi thấy chỗ đã có xe, tệ hơn là chưa từng có tính năng này. Slot được chọn **row-locked**, nên hai tài xế tranh chỗ cuối cùng không thể cùng được cấp.
- **BR-009-3b (Admin không được sửa slot đang bị giữ):** `PATCH /slots/{id}/status`, `DELETE /slots/{id}` và `POST /slots/provision` (khi thu nhỏ zone) đều **từ chối 409** với slot đang có lượt `HELD` — kèm mã slot để admin biết chờ hết hạn hay yêu cầu hủy. Kiểm tra dựa vào **lượt đặt**, không dựa vào cờ `slots.status`: admin vừa lật cờ về `EMPTY` rồi xóa slot sẽ để lại lượt đặt trỏ vào hư không, còn xe đã đặt lúc vào bãi sẽ đè lên slot đã có xe khác.
- **BR-009-4 (`RESERVED` tính là đã dùng):** Trong mọi phép đếm sức chứa — ngưỡng "bãi đầy" (BR-002-5/BR-003-5) và `occupancyRate` — `RESERVED` nằm ở **phía đã dùng**. Ngược lại, bảng điều khiển sẽ báo "còn nửa bãi" trong khi cổng đang từ chối xe.
  - Hệ quả: bãi có thể **đầy vì đặt chỗ**. Đây là đánh đổi có chủ đích — chắc chắn cho người đã đặt, đổi lấy chỗ trống đứng chờ. `hold-minutes` ngắn là thứ giữ cho cái giá đó không quá đắt.
- **BR-009-5 (Một lượt đang giữ / biển):** Mỗi biển chỉ có tối đa **1 lượt `HELD`** — cùng tinh thần BR-002-4 (1 session ACTIVE/biển). Ràng buộc là **unique index từng phần** trong DB, không phải chỉ kiểm tra ở tầng service.
- **BR-009-6 (Xe có đặt chỗ vào bãi):** Khi xe quét VÀO, nếu biển có lượt `HELD` còn sống thì nhận **đúng slot đã giữ**, và kiểm tra này chạy **trước** kiểm tra bãi đầy — slot của họ vốn đã ngoài pool, "đầy" không được phép từ chối họ. Lượt đặt chuyển `FULFILLED` và gắn `session_id`.
- **BR-009-7 (Hủy):** Tài xế hủy được khi lượt còn `HELD`, và **chỉ lượt của chính mình**. Đã `FULFILLED`/`EXPIRED`/`CANCELLED` thì không hủy nữa. Slot lập tức về `EMPTY` — trừ khi slot đang `OCCUPIED` (đã có xe đậu thật), lúc đó để nguyên, vì lật về `EMPTY` là trao cùng một chỗ cho người thứ hai.
- **BR-009-8 (Bỏ hẹn nhiều lần):** Biển có ≥ `no-show-limit` lượt `EXPIRED` (mặc định **3**) trong `no-show-window-days` (mặc định **30 ngày**) bị **tạm khóa quyền đặt chỗ**. Người giữ chỗ rồi không đến đang chặn chỗ của khách sẵn sàng trả tiền, nên phải có cái giá. **Không thu tiền cọc** — cọc kéo theo hoàn tiền, tranh chấp và một luồng thanh toán thứ hai, quá đắt so với thứ nó giải quyết.
- **BR-009-9 (Chưa tính tiền giữ chỗ):** Đặt chỗ **miễn phí**; hóa đơn vẫn tính từ `entry_time` như mọi xe khác (BR-004-5). Thời gian giữ chỗ trước khi xe vào không tính tiền. Nếu sau này muốn thu phí giữ chỗ, đó là thay đổi ở BR-004 và cần ADR riêng.
