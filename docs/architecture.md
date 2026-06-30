# Architecture – Smart Parking System

## 1. Kiến Trúc Tổng Thể

Hệ thống theo kiến trúc **Event-Driven Microservices** với 4 service độc lập, giao tiếp qua Apache Kafka (async) và REST API (sync query-only).

```
┌─────────────────────────────────────────────────────────────────┐
│                         EDGE LAYER                              │
│  [Camera IP – RTSP]  ──>  [edge-agent :8000]                   │
│                              │ produce                          │
│                         plate-detected                          │
└──────────────────────────────┼──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      MESSAGING LAYER                            │
│                  Apache Kafka (KRaft :9092)                     │
│  Topics: plate.detected | gate.command | gate.state | session.* | billing.*  │
└─────────┬─────────────────────────────────────┬─────────────────┘
          │ consume                             │ consume
┌─────────▼──────────┐              ┌───────────▼────────────────┐
│  parking-service   │              │     billing-service         │
│      :8081         │ ──session──> │         :8082              │
│   parking_db:5434  │   closed     │     billing_db:5435        │
└─────────┬──────────┘              └────────────────────────────┘
          │                                     │
          │ REST (query only)                   │ REST (query only)
          ▼                                     ▼
┌─────────────────────────────────────────────────┐
│              admin-service :8083                │
│                admin_db:5433                    │
└─────────────────────────────────────────────────┘
          │
          │ REST
          ▼
┌─────────────────────────────────────────────────┐
│          Next.js Admin Dashboard :3000          │
└─────────────────────────────────────────────────┘
          │
          │ scrape /actuator/prometheus
          ▼
┌─────────────────────────────────────────────────┐
│    Prometheus :9090  →  Grafana :3001           │
└─────────────────────────────────────────────────┘
```

## 2. Luồng Nghiệp Vụ Chính

### 2.1. Xe vào bãi (Entry Flow)
```
1. Xe dừng tại cổng vào
2. Camera trigger → edge-agent nhận frame
3. YOLOv8-s phát hiện xe → detect biển số
4. EasyOCR trích xuất ký tự → validate regex VN plate
5. edge-agent PRODUCE: parking.plate.detected {plateNumber, gateId:ENTRY, direction:IN}
6. parking-service CONSUME plate.detected
7. parking-service kiểm tra whitelist/blacklist
8. parking-service tạo Session PENDING → ghi outbox
9. Outbox thread PRODUCE: parking.gate.command {gateId:ENTRY, command:OPEN}
10. edge-agent CONSUME gate.command → relay controller → barie mở
    → edge-agent PRODUCE parking.gate.state {status:OPEN, reason:command}
    → barie auto-đóng sau GATE_AUTO_CLOSE_SECONDS (BR-006-2)
    → edge-agent PRODUCE parking.gate.state {status:CLOSED, reason:auto}
    → parking-service CONSUME gate.state → đồng bộ gates.status (web/mobile không kẹt OPEN)
11. Cảm biến xác nhận xe qua → edge-agent PRODUCE gate.opened
12. parking-service: Session ACTIVE, Slot OCCUPIED
13. parking-service PRODUCE: parking.session.created
14. billing-service CONSUME session.created → tạo Invoice PENDING
```

### 2.2. Xe ra bãi (Exit Flow)
```
1. Xe dừng tại cổng ra
2. edge-agent nhận diện biển số → PRODUCE plate.detected {direction:OUT}
3. parking-service match sessionId theo plateNumber
4. parking-service tính durationSeconds = exitTime - entryTime
5. parking-service: Session CLOSED, Slot EMPTY
6. parking-service PRODUCE: parking.session.closed {sessionId, durationSeconds}
7. billing-service CONSUME session.closed → tính phí
8. billing-service: Invoice amount = duration × rate (peak nếu cần)
9. billing-service PRODUCE: billing.invoice.calculated
10. Operator xác nhận thanh toán (cash/QR)
11. billing-service: Invoice PAID → PRODUCE payment.completed
12. parking-service nhận signal → PRODUCE gate.command {OPEN cổng ra}
13. Xe ra → barie đóng
```

### 2.3. Saga Compensation (lỗi trong Entry Flow)
```
Nếu gate.opened không nhận được trong 10s sau gate.command OPEN:
→ parking-service publish gate.command {CLOSE}
→ parking-service xóa Session PENDING
→ Alert gửi đến admin-service
→ Operator nhận thông báo để xử lý thủ công
```

## 3. Quyết Định Kiến Trúc (Architecture Decision Records)

### ADR-001: Chọn Kafka thay vì RabbitMQ
- **Lý do:** Log retention 7 ngày cho phép replay event khi service restart. Consumer group dễ scale. KRaft mode không cần ZooKeeper.

### ADR-002: Database Per Service
- **Lý do:** Độc lập deploy, độc lập schema migration, tránh coupling. Cost: cross-service query phải qua API/event thay vì JOIN.

### ADR-003: Outbox Pattern cho Kafka publish
- **Lý do:** Đảm bảo at-least-once: DB transaction và Kafka publish là atomic. Không mất event khi Kafka tạm thời down.

### ADR-004: Choreography Saga (không dùng Orchestrator)
- **Lý do:** Tránh single point of failure. Mỗi service tự biết compensating action của mình. Phù hợp với quy mô 4 service.

### ADR-005: YOLOv8-s cho edge inference
- **Lý do:** mAP 99.3% @ 30+ FPS trên CPU. Giai đoạn RBL dùng giả lập (simulate/trigger), không cần GPU thực.
