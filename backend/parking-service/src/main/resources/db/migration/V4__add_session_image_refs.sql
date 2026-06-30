-- Ảnh chụp xe lúc VÀO/RA (object key trong MinIO) để operator/tài xế truy vết sau này.
-- imageRef đến từ event parking.plate.detected (edge-agent đã upload frame lên object storage).
ALTER TABLE sessions ADD COLUMN entry_image_ref VARCHAR(300);
ALTER TABLE sessions ADD COLUMN exit_image_ref  VARCHAR(300);
