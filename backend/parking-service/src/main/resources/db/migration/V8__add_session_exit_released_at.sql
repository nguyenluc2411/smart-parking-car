-- Mốc "cổng ra đã thực sự mở cho đúng phiên này" (khác gates.status, vốn tự đóng lại theo BR-006-2
-- và bị tái sử dụng qua nhiều phiên khác nhau). NULL = xe chưa thực sự ra (đang chờ thanh toán khi
-- status=CLOSED); có giá trị = cổng đã mở cho phiên này (BR-005-4/5/6).
ALTER TABLE sessions ADD COLUMN exit_released_at TIMESTAMPTZ;
