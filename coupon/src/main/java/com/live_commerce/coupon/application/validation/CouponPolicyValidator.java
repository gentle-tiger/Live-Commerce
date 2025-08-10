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


  /** 생성 시, 활성 정책(code) 중복만 검사 → 나머지 순수 규칙은 엔티티가 보장 */
  public void validateForCreatePolicy(CreateCouponPolicyRequest req){
    // "활성(미삭제)" 기준 중복 체크 메서드를 권장합니다.
    if(couponPolicyRepository.existByCodeAndDeletedStatusFalse(req.code())){
      throw CouponPolicyException.duplicate(req.code());
    }
  }

  @Deprecated
  public void validateForUpdatePolicy(UpdateCouponPolicyRequest req){
    // 필요 시: 동일 code 내 name 중복 금지, 기간 겹침 금지(요구사항 있다면) 등 저장소 조회가 필요한 규칙만 여기에
  }



}
