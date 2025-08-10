package com.live_commerce.coupon.domain.exception;

import com.live_commerce.coupon.application.exception.CustomException;
import com.live_commerce.coupon.application.exception.IssuedCouponExceptionCode;
import java.util.UUID;

public class IssuedCouponException extends CustomException {

  public IssuedCouponException(IssuedCouponExceptionCode code, Object... args) {
    super(code, args); // 메시지는 핸들러에서 i18n으로 조립
  }


  public static IssuedCouponException notFound(UUID couponId, UUID userId) {
    return new IssuedCouponException(IssuedCouponExceptionCode.ISSUED_COUPON_NOT_FOUND, couponId,
        userId);
  }

  public static IssuedCouponException alreadyUsed(UUID couponId) {
    return new IssuedCouponException(IssuedCouponExceptionCode.ISSUED_ALREADY_USED, couponId);
  }

  public static IssuedCouponException alreadyIssued(UUID couponId, String couponCode) {
    return new IssuedCouponException(IssuedCouponExceptionCode.COUPON_ALREADY_ISSUED, couponId,
        couponCode);
  }

  public static IssuedCouponException expired(UUID couponId, UUID userId) {
    return new IssuedCouponException(IssuedCouponExceptionCode.ISSUED_COUPON_EXPIRED, couponId,
        userId);
  }

  public static IssuedCouponException ownershipMismatch(UUID couponId, UUID userId) {
    return new IssuedCouponException(IssuedCouponExceptionCode.OWNERSHIP_MISMATCH, couponId,
        userId);
  }


}
