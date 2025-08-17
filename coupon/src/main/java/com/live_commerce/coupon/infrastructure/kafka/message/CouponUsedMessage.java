package com.live_commerce.coupon.infrastructure.kafka.message;


import java.time.Instant;
import java.util.UUID;

/* 외부 전송용 스키마(버전 포함) */
public record CouponUsedMessage(
    UUID couponId,
    UUID userId,
    Instant occurredAt, // 인스턴트 타입의 발행 날짜?
    String eventType,
    int version // 버전 관리??
) {}
