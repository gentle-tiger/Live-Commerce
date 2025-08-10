package com.live_commerce.coupon.domain.exception;

import com.live_commerce.coupon.application.exception.CouponPolicyExceptionCode;
import com.live_commerce.coupon.application.exception.CustomException;

public class CouponPolicyException extends CustomException {

  public CouponPolicyException(CouponPolicyExceptionCode code, Object... args) {
    super(code,args); // 메시지는 핸들러에서 i18n으로 조립
  }

  public static CouponPolicyException notFound(String policyCode){
    return new CouponPolicyException(CouponPolicyExceptionCode.COUPON_POLICY_NOT_FOUND, policyCode);
  }
  public static CouponPolicyException duplicate(String policyCode){
    return new CouponPolicyException(CouponPolicyExceptionCode.DUPLICATE_COUPON_CODE, policyCode);
  }

  public static CouponPolicyException invalidDateRange(){
    return new CouponPolicyException(CouponPolicyExceptionCode.INVALID_DATE_RANGE);
  }

  public static CouponPolicyException discountGreaterThanMaxOrderAmount(){
    return new CouponPolicyException(CouponPolicyExceptionCode.DISCOUNT_GREATER_THAN_MAX_ORDER_AMOUNT);
  }

  public static CouponPolicyException discountGreaterThan100(){
    return new CouponPolicyException(CouponPolicyExceptionCode.DISCOUNT_GREATER_THAN_100);
  }

}