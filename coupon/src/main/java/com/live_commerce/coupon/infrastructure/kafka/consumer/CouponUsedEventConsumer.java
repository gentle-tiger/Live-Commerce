package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponUsedEventConsumer {

  private final IssuedCouponService issuedCouponService;

  @KafkaListener(
          topics = "coupon-used",
          groupId = "${spring.application.name}"
  )
  public void onCouponUsed(CouponUsedEvent msg) {
    issuedCouponService.handleCouponUsedEvent(msg.couponId(), msg.userId());
    log.info("✅ 쿠폰 사용 성공 이벤트 수신(kafka): couponId={}, userId={}", msg.couponId(), msg.userId());
  }
}