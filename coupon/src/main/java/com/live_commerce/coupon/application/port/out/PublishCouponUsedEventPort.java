package com.live_commerce.coupon.application.port.out;

import java.util.UUID;

/** 외부 시스템(Kafka 등)으로 통합 이벤트 발행 */

public interface PublishCouponUsedEventPort {
  void publishCouponUsedEvent(UUID couponId, UUID userId);
}
