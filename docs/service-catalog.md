# Service Catalog – Smart Parking System

## edge-agent

| Thuộc tính | Giá trị |
|---|---|
| Runtime | Python 3.11 + FastAPI 0.110 |
| Port | 8000 |
| Database | KHÔNG CÓ (stateless) |
| Auth | X-API-Key header |

### Endpoints
| Method | Path | Mô tả |
|---|---|---|
| POST | /api/v1/detect | Nhận frame → chạy ALPR → publish event |
| POST | /api/v1/simulate/trigger | Giả lập trigger camera (dev/test) |
| GET | /api/v1/config | Xem config hiện tại |
| PUT | /api/v1/config | Update config runtime |
| GET | /health | Health check (model + Kafka) |
| GET | /metrics | Prometheus metrics |

### Dependencies
- Kafka (PRODUCE plate.detected + gate.state, CONSUME gate.command)
- parking-service (health dependency)
- YOLOv8-s model file tại /app/models/yolov8s_vn.pt

---

## parking-service

| Thuộc tính | Giá trị |
|---|---|
| Runtime | Java 17 + Spring Boot 3.2 |
| Port | 8081 |
| Database | parking_db (PostgreSQL :5434) |
| Auth | Bearer JWT (validate tại service) |
| Package | com.smartparking.parking |

### Endpoints
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | /api/v1/sessions | OPERATOR,ADMIN | Danh sách phiên |
| GET | /api/v1/sessions/{id} | OPERATOR,ADMIN | Chi tiết phiên |
| GET | /api/v1/slots | OPERATOR,ADMIN | Trạng thái slot |
| GET | /api/v1/slots/availability | OPERATOR,ADMIN | Slot trống/tổng |
| POST | /api/v1/slots | ADMIN | Tạo slot |
| DELETE | /api/v1/slots/{id} | ADMIN | Xóa slot |
| PATCH | /api/v1/slots/{id}/status | ADMIN | Đặt EMPTY/MAINTENANCE |
| POST | /api/v1/slots/provision | ADMIN | Cấu hình nhanh theo khu |
| POST | /api/v1/vehicles/whitelist | ADMIN | Thêm whitelist |
| DELETE | /api/v1/vehicles/whitelist/{plate} | ADMIN | Xóa whitelist |
| GET | /api/v1/vehicles/blacklist | OPERATOR,ADMIN | Danh sách blacklist |
| POST | /api/v1/vehicles/blacklist | ADMIN | Thêm blacklist |
| DELETE | /api/v1/vehicles/blacklist/{plate} | ADMIN | Xóa blacklist |
| GET | /api/v1/gates | OPERATOR,ADMIN | Trạng thái cổng |
| POST | /api/v1/gates/{id}/override | ADMIN | Override thủ công |
| GET | /actuator/health | – | Health check |
| GET | /actuator/prometheus | – | Metrics |

### Kafka
- CONSUME: `parking.plate.detected`, `parking.gate.state`
- PRODUCE: `parking.gate.command`, `parking.session.created`, `parking.session.closed`

### DB Tables
`sessions`, `slots`, `vehicles`, `gates`, `gate_logs`, `outbox_events`

---

## billing-service

| Thuộc tính | Giá trị |
|---|---|
| Runtime | Java 17 + Spring Boot 3.2 |
| Port | 8082 |
| Database | billing_db (PostgreSQL :5435) |
| Auth | Bearer JWT |
| Package | com.smartparking.billing |

### Endpoints
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | /api/v1/billing/sessions/{id} | OPERATOR,ADMIN | Chi tiết hóa đơn |
| POST | /api/v1/billing/sessions/{id}/pay | OPERATOR | Xác nhận thanh toán |
| GET | /api/v1/billing/rates | OPERATOR,ADMIN | Bảng giá hiện tại |
| PUT | /api/v1/billing/rates | ADMIN | Cập nhật bảng giá |
| GET | /api/v1/billing/report/daily | ADMIN | Báo cáo ngày |
| GET | /api/v1/billing/report/monthly | ADMIN | Báo cáo tháng |
| GET | /api/v1/billing/report/export | ADMIN | Xuất CSV |
| GET | /actuator/health | – | Health check |
| GET | /actuator/prometheus | – | Metrics |

### Kafka
- CONSUME: `parking.session.closed`
- PRODUCE: `billing.invoice.calculated`, `billing.payment.completed`

### DB Tables
`invoices`, `rates`, `rate_schedules`, `payments`, `outbox_events`

---

## admin-service

| Thuộc tính | Giá trị |
|---|---|
| Runtime | Java 17 + Spring Boot 3.2 |
| Port | 8083 |
| Database | admin_db (PostgreSQL :5433) |
| Auth | Bearer JWT (issuer – service này tạo token) |
| Package | com.smartparking.admin |

### Endpoints
| Method | Path | Role | Mô tả |
|---|---|---|---|
| POST | /api/v1/auth/login | PUBLIC | Đăng nhập → JWT |
| POST | /api/v1/auth/refresh | JWT | Gia hạn token |
| POST | /api/v1/auth/logout | JWT | Blacklist token |
| GET | /api/v1/users | ADMIN | Danh sách users |
| POST | /api/v1/users | ADMIN | Tạo user mới |
| PUT | /api/v1/users/{id}/role | ADMIN | Đổi role |
| DELETE | /api/v1/users/{id} | ADMIN | Vô hiệu hóa |
| GET | /api/v1/audit-logs | ADMIN | Lịch sử thao tác |
| GET | /api/v1/system/config | ADMIN | Xem system config |
| PUT | /api/v1/system/config | ADMIN | Cập nhật config |
| GET | /actuator/health | – | Health check |
| GET | /actuator/prometheus | – | Metrics |

### Kafka
- CONSUME: tất cả topics (ghi audit log)
- PRODUCE: không

### DB Tables
`users`, `refresh_tokens`, `audit_logs`, `system_config`

---

## Roles & Permissions Matrix

| Quyền | PUBLIC | OPERATOR | ADMIN |
|---|---|---|---|
| Login | ✓ | ✓ | ✓ |
| Xem slot/session | – | ✓ | ✓ |
| Xác nhận thanh toán | – | ✓ | ✓ |
| Ghi nhận sự cố | – | ✓ | ✓ |
| Override barie | – | – | ✓ |
| Quản lý whitelist | – | – | ✓ |
| Cấu hình bảng giá | – | – | ✓ |
| Xem báo cáo doanh thu | – | – | ✓ |
| Quản lý users | – | – | ✓ |
| Xem audit log | – | – | ✓ |
| Cập nhật system config | – | – | ✓ |
