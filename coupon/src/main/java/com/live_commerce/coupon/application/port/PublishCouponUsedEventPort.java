package com.live_commerce.coupon.application.port;


import java.util.UUID;


public interface PublishCouponUsedEventPort {
  void publishCouponUsedEvent(UUID couponId, UUID userId);
}
