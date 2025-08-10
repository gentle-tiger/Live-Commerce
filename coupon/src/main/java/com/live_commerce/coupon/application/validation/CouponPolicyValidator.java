package com.live_commerce.coupon.application.validation;

import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.repository.CouponPolicyRepository;
import com.live_commerce.coupon.presentation.dto.request.CreateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.request.UpdateCouponPolicyRequest;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CouponPolicyValidator {

  private final CouponPolicyRepository couponPolicyRepository;

  public CouponPolicyValidator(CouponPolicyRepository couponPolicyRepository) {
    this.couponPolicyRepository = couponPolicyRepository;
  }

  public void validateForCreatePolicy(CreateCouponPolicyRequest request) {
    if (couponPolicyRepository.existsById(request.code())) {
      CouponPolicyException.duplicate(request.code());
    }

    if (request.startAt().isAfter(request.endAt())) {
      CouponPolicyException.invalidDateRange(request.code());
    }

    if (request.discountType() == DISCOUNT_TYPE.FIXED
        && request.discountValue().compareTo(request.maxOrderAmt()) > 0) {
      CouponPolicyException.discountGreaterThanMaxOrderAmount();
    }

    if (request.discountType() == DISCOUNT_TYPE.RATE
        && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      CouponPolicyException.discountGreaterThan100();
    }
  }

  public void validateForUpdatePolicy(UpdateCouponPolicyRequest request) {
    if (request.startAt().isAfter(request.endAt())) {
      CouponPolicyException.invalidDateRange(request.name()); // 사실 이거 name이 아니라 code 가 가야한느거 ... 임시... code도 정확한 거 아님..
    }

    if (request.discountType() == DISCOUNT_TYPE.FIXED
        && request.discountValue().compareTo(request.maxOrderAmt()) > 0) {
      CouponPolicyException.discountGreaterThanMaxOrderAmount();
    }

    if (request.discountType() == DISCOUNT_TYPE.RATE
        && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
      CouponPolicyException.discountGreaterThan100();
    }
  }
}
