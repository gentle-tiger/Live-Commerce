package com.live_commerce.coupon.domain.event;

import java.util.UUID;

public record CouponUsedEvent (UUID couponId, UUID userId){

}
