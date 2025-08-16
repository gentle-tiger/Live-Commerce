package com.live_commerce.coupon.presentation.dto.response;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.UUID;

public record UsedIssuedCouponResponse(
    UUID issuedCouponId,
    String couponCode,
    UUID userId,
    boolean isUsed,
    LocalDateTime usedAt,
    LocalDateTime expiresAt
) {

  public static UsedIssuedCouponResponse from(IssuedCoupon issuedCoupon) {
    return new UsedIssuedCouponResponse(
        issuedCoupon.getId(),
        issuedCoupon.getCouponCode(),
        issuedCoupon.getUserId(),
        issuedCoupon.isUsed(),
        issuedCoupon.getUsedAt(),
        issuedCoupon.getExpiresAt()
    );
  }
}