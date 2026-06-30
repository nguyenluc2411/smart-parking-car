package com.smartparking.parking.service.impl;

import com.smartparking.parking.service.ImageUrlService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Presigns GET URLs for snapshots in MinIO. Disabled (returns {@code null}) when no credentials are
 * configured, so the feature degrades gracefully in environments without object storage.
 */
@Slf4j
@Service
public class ImageUrlServiceImpl implements ImageUrlService {

    private final MinioClient minioClient;
    private final String bucket;
    private final int expiryMinutes;
    private final boolean enabled;

    public ImageUrlServiceImpl(MinioClient minioClient,
                               @Value("${app.minio.bucket:parking-frames}") String bucket,
                               @Value("${app.minio.presign-expiry-minutes:15}") int expiryMinutes,
                               @Value("${app.minio.access-key:}") String accessKey,
                               @Value("${app.minio.secret-key:}") String secretKey) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.expiryMinutes = expiryMinutes;
        this.enabled = !accessKey.isBlank() && !secretKey.isBlank();
    }

    @Override
    public String presignedPlateCrop(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return presignedGet(plateCropKey(objectKey));
    }

    /** Sibling key for the plate close-up — mirrors edge-agent FrameStorage._plate_key. */
    static String plateCropKey(String objectKey) {
        String base = objectKey.endsWith(".jpg")
                ? objectKey.substring(0, objectKey.length() - ".jpg".length())
                : objectKey;
        return base + ".plate.jpg";
    }

    @Override
    public String presignedGet(String objectKey) {
        if (!enabled || objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            // A presign failure must not break the session-detail response — just omit the URL.
            log.warn("Failed to presign URL for object '{}': {}", objectKey, e.getMessage());
            return null;
        }
    }
}
