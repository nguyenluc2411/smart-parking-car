package com.smartparking.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * General application beans.
 *
 * <p>{@link RestTemplate} is used by {@code TelegramNotificationServiceImpl} to call the
 * Telegram Bot API. A single shared instance is sufficient — RestTemplate is thread-safe.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
