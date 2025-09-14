package com.live_commerce.coupon.infrastructure.config;

import com.live_commerce.coupon.infrastructure.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  @Bean
  AuthenticationFilter authenticationFilter() {
    return new AuthenticationFilter();
  }

  @Bean
  FilterRegistrationBean<AuthenticationFilter> disableGlobalRegistration(AuthenticationFilter f) {
    var reg = new FilterRegistrationBean<>(f);
    reg.setEnabled(false); // ★ 전역 등록 끔
    return reg;
  }

  // 1) /actuator/** 전용: 인증/필터 모두 제외
  @Bean @Order(0)
  SecurityFilterChain actuator(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(EndpointRequest.toAnyEndpoint()) // Actuator 전용
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .build();
  }

  @Bean @Order(1)
  public SecurityFilterChain app(HttpSecurity http, AuthenticationFilter authenticationFilter) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // ✅ 공개 엔드포인트
            .requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/api/v1/auth/**",
                "/api/v1/issued-coupons/*/signup-first"
            ).permitAll()
            // ✅ 우선 인증만 요구(403 원인 파악용)
            .requestMatchers(HttpMethod.POST, "/api/v1/coupon-policies").authenticated()
            // 나머지는 인증 필요
            .anyRequest().authenticated()
        )
        .build();
  }
}