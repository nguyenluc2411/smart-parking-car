package com.smartparking.parking.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client used ONLY to sign presigned GET URLs for entry/exit snapshots.
 *
 * <p>The client is configured with the <em>public</em> endpoint (e.g. {@code localhost:9000}) because
 * a presigned URL is signed for a specific host and must be reachable by the browser/mobile — not the
 * internal {@code minio:9000} host. A fixed {@code region} is set so the SDK skips its bucket-location
 * lookup (a network call to the endpoint): presigning then stays purely local, so this bean does not
 * need to reach the public endpoint from inside the container.
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${app.minio.public-endpoint:localhost:9000}") String publicEndpoint,
            @Value("${app.minio.access-key:}") String accessKey,
            @Value("${app.minio.secret-key:}") String secretKey,
            @Value("${app.minio.region:us-east-1}") String region,
            @Value("${app.minio.secure:false}") boolean secure) {
        String url = (secure ? "https://" : "http://") + publicEndpoint;
        return MinioClient.builder()
                .endpoint(url)
                .region(region)            // avoid the getBucketLocation network call at presign time
                .credentials(accessKey, secretKey)
                .build();
    }
}
