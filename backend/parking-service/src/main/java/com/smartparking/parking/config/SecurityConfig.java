package com.smartparking.parking.config;

import com.smartparking.parking.security.JwtAuthenticationFilter;
import com.smartparking.parking.security.RestAuthenticationEntryPoint;
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
 * Stateless JWT security (tokens issued by admin-service). Rules per docs/api-contracts.md:
 * whitelist management is ADMIN-only; gate override + read endpoints are OPERATOR/ADMIN;
 * actuator is public. (REST controllers are forthcoming; this config protects them on arrival.)
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/slots/resync").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/slots", "/api/v1/slots/provision").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/slots/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/slots/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/whitelist").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vehicles/whitelist/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehicles/blacklist").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vehicles/blacklist/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/gates/*/override").hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/v1/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
