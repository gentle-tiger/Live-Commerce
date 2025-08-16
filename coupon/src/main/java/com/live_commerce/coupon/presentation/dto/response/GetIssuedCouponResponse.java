package com.live_commerce.coupon.presentation.dto.response;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

public record GetIssuedCouponResponse(
    UUID id,
    UUID userId,
    String couponCode,
    boolean isUsed,
    LocalDateTime expiresAt
) {

  public static GetIssuedCouponResponse from(IssuedCoupon issuedCoupon) {
    return new GetIssuedCouponResponse(
        issuedCoupon.getId(),
        issuedCoupon.getUserId(),
        issuedCoupon.getCouponCode(),
        issuedCoupon.isUsed(),
        issuedCoupon.getExpiresAt()
    );
  }

  public static List<GetIssuedCouponResponse> from(List<IssuedCoupon> issuedCoupons) {
    return issuedCoupons.stream()
        .map(GetIssuedCouponResponse::from)
        .collect(Collectors.toList());
  }
}

