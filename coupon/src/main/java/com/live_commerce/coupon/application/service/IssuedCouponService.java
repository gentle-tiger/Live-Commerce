package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.application.port.IssueFirstJoinCouponPort;
import com.live_commerce.coupon.application.port.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.CouponPolicyRepository;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import com.live_commerce.coupon.presentation.dto.response.FirstJoinCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.GetIssuedCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.IssuedCouponListResponse;
import com.live_commerce.coupon.presentation.dto.response.UsedIssuedCouponResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class IssuedCouponService {

  private final IssuedCouponRepository issuedCouponRepository;
  private final CouponPolicyRepository couponPolicyRepository;
  // private final IssueFirstJoinCouponPort firstJoinCouponPort;
  // private final PublishCouponUsedEventPort publishCouponUsedEventPort;

  public IssuedCoupon issueCoupon(IssuedCouponRequest request, RequestUserDetails userDetails) {

    CouponPolicy couponPolicy = couponPolicyRepository.findByCodeAndDeletedStatusFalse(
        request.couponCode()).orElseThrow();

    IssuedCoupon issuedCoupon = IssuedCoupon.from(request, couponPolicy, userDetails.getUserId());

    issuedCoupon = issuedCouponRepository.save(issuedCoupon);
    return issuedCoupon;
  }

  public IssuedCoupon issueFirstCoupon(IssuedCouponRequest request, UUID userId) {
    CouponPolicy couponPolicy = couponPolicyRepository.findByCodeAndDeletedStatusFalse(
        request.couponCode()).orElseThrow();

    IssuedCoupon issuedCoupon = IssuedCoupon.from(request, couponPolicy, userId);
    return issuedCouponRepository.save(issuedCoupon);
  }


  public IssuedCoupon useCoupon(UUID couponId, RequestUserDetails userDetails) {

    // TODO: 사용하지 않으 쿠폰만 조회
    IssuedCoupon issuedCoupon = findIssuedCouponByIdAndUser(couponId, userDetails);

    checkIfCouponUsed(issuedCoupon);

    return processCouponUsage(issuedCoupon);
  }

  private IssuedCoupon findIssuedCouponByIdAndUser(UUID couponId, RequestUserDetails userDetails) {
    return issuedCouponRepository.findByIdAndUserIdAndIsUsedFalse(couponId, userDetails.getUserId())
        .orElseThrow(() -> {
          IssuedCouponException.issuedCouponNotFound();
          return null;
        });
  }

  private void checkIfCouponUsed(IssuedCoupon issuedCoupon) {
    if (issuedCoupon.isUsed()) {
      IssuedCouponException.alreadyUsedCoupon();
    }
  }

  private IssuedCoupon processCouponUsage(IssuedCoupon issuedCoupon) {
    issuedCoupon.useCoupon();
    return issuedCouponRepository.save(issuedCoupon);
  }

  @Transactional(readOnly = true)
  public GetIssuedCouponResponse getIssuedCoupon(UUID couponId, RequestUserDetails userDetails) {
    IssuedCoupon issuedCoupon = findByIdAndUserAndIsUsedFalse(couponId, userDetails);
    return GetIssuedCouponResponse.from(issuedCoupon);
  }

  private IssuedCoupon findByIdAndUserAndIsUsedFalse(UUID couponId,
      RequestUserDetails userDetails) {
    return issuedCouponRepository.findByIdAndUserIdAndIsUsedFalse(couponId, userDetails.getUserId())
        .orElseThrow(() ->
        {
          IssuedCouponException.issuedCouponNotFound();
          return null;
        });
  }

  @Transactional(readOnly = true)
  public IssuedCouponListResponse getIssuedCoupons(RequestUserDetails userDetails) {
    //TODO : 사용자한정
    List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findByUserId(userDetails.getUserId());
    return IssuedCouponListResponse.from(issuedCoupons);
  }

  public FirstJoinCouponResponse issueFirstCoupon(UUID userId) {
    String couponCode = "FIRST_COUPON";
    CouponPolicy couponPolicy = createFirstCouponPolicy(couponCode);

    IssuedCouponRequest request = new IssuedCouponRequest(couponCode);
    IssuedCoupon issuedCoupon = issueFirstCoupon(request, userId); // userId 기반

    return FirstJoinCouponResponse.from(issuedCoupon);
  }


  private CouponPolicy createFirstCouponPolicy(String couponCode) {

    CouponPolicy couponPolicy = CouponPolicy.builder()
        .code(couponCode)
        .name("First Coupon for Signup")
        .discountType(DISCOUNT_TYPE.FIXED)
        .discountValue(BigDecimal.valueOf(15000))
        .minOrderAmt(BigDecimal.valueOf(0))
        .maxOrderAmt(BigDecimal.valueOf(50000))
        .startAt(LocalDateTime.now())
        .endAt(LocalDateTime.now().plusYears(1))
        .isActive(true)
        .build();
    couponPolicyRepository.save(couponPolicy);
    return couponPolicy;

  }

  // public void issueFirstCouponOnSignup(UUID userId) {
  //   firstJoinCouponPort.publishFirstJoinEvent(userId);
  // }

  public void issueFirstCouponDirectly(UUID userId) {
    String couponCode = "FIRST_COUPON";
    CouponPolicy couponPolicy = createFirstCouponPolicy(couponCode);

    IssuedCouponRequest request = new IssuedCouponRequest(couponCode);
    IssuedCoupon issuedCoupon = issueFirstCoupon(request, userId);
    FirstJoinCouponResponse.from(issuedCoupon);
  }

  public UsedIssuedCouponResponse useCouponAndPublishEvent(UUID couponId,
      RequestUserDetails userDetails) {
    UUID userId = userDetails.getUserId();
    IssuedCoupon issued = useCoupon(couponId, userDetails);
    // publishCouponUsedEventPort.publishCouponUsedEvent(couponId, userId);
    return UsedIssuedCouponResponse.from(issued);
  }

  public void handleCouponUsedEvent(UUID couponId, UUID userId) {
    log.info("✅ 쿠폰 사용 후처리 시작: couponId={}, userId={}", couponId, userId);

    // 1) 미사용 쿠폰 조회 (Repository 직접 호출)
    IssuedCoupon issuedCoupon = issuedCouponRepository
            .findByIdAndUserIdAndIsUsedFalse(couponId, userId)
            .orElseThrow(() -> {
              IssuedCouponException.issuedCouponNotFound();
              return null;
            });

    checkIfCouponUsed(issuedCoupon);
    processCouponUsage(issuedCoupon);
  }
}