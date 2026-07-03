package com.smartparking.billing.config;

import com.smartparking.billing.security.JwtAuthenticationFilter;
import com.smartparking.billing.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security (tokens issued by admin-service). Per docs/api-contracts.md:
 * reports are ADMIN-only; invoice + pay are OPERATOR/ADMIN; actuator is public.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        // BR-005-2: MoMo IPN webhook — public (no JWT); authenticity via HMAC signature.
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/momo/ipn").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/payos/webhook").permitAll()
                        .requestMatchers("/api/v1/billing/report/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/billing/rates").hasRole("ADMIN")
                        .requestMatchers("/api/v1/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/v1/billing/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
