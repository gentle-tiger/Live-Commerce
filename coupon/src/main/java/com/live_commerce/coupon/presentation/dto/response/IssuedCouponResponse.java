package com.live_commerce.coupon.presentation.dto.response;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.UUID;

public record IssuedCouponResponse(

    UUID id,
    UUID userId,
    String couponCode,
    Boolean isUsed,
    LocalDateTime usedAt,
    LocalDateTime expiresAt
) {

  public static IssuedCouponResponse fromEntity(IssuedCoupon issuedCoupon) {
    return new IssuedCouponResponse(
        issuedCoupon.getId(),
        issuedCoupon.getUserId(),
        issuedCoupon.getCouponCode(),
        issuedCoupon.isUsed(),
        issuedCoupon.getUsedAt(),
        issuedCoupon.getExpiresAt()
    );
  }
}