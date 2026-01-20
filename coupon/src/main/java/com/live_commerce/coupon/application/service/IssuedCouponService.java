package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.domain.model.CouponUseStatus;
import org.springframework.context.ApplicationEventPublisher;

import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import com.live_commerce.coupon.domain.exception.CouponPolicyException;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
public class IssuedCouponService {

  private final IssuedCouponRepository issuedCouponRepository;
  private final CouponPolicyRepository couponPolicyRepository;

  private final ApplicationEventPublisher eventPublisher;

  // 쿠폰 발급
  public IssuedCoupon issueCoupon(IssuedCouponRequest request, RequestUserDetails userDetails) {
    CouponPolicy couponPolicy = couponPolicyRepository
        .findByCodeAndDeletedStatusFalse(request.couponCode())
        .orElseThrow(() -> CouponPolicyException.notFound(request.couponCode()));

    IssuedCoupon issuedCoupon = IssuedCoupon.from(request, couponPolicy, userDetails.getUserId());
    return issuedCouponRepository.save(issuedCoupon);
  }


  // 첫 회원가입 시 쿠폰 발급
  public IssuedCoupon issueFirstCoupon(IssuedCouponRequest request, UUID userId) {
    CouponPolicy couponPolicy = couponPolicyRepository
        .findByCodeAndDeletedStatusFalse(request.couponCode())
        .orElseThrow(() -> CouponPolicyException.notFound(request.couponCode()));

    IssuedCoupon issuedCoupon = IssuedCoupon.from(request, couponPolicy, userId);
    return issuedCouponRepository.save(issuedCoupon);
  }

  // 쿠폰 소진

  /**
   * 낙관적 락 충돌 시 수종 재시도 + 지수 백오프 (최대 3회) 트랜잭션은 시도마다 REQUIRES_NEW로 새로 연다.
   */
//  @Transactional(readOnly = true) // 바깥은 readOnly; 실사용은 내부 REQUIRES_NEW에서 처리 // 이거 떄문에 동시성 테스트를 통과하지 못했던 것임.
  public IssuedCoupon useCoupon(UUID couponId, RequestUserDetails userDetails) {
    final UUID userId = userDetails.getUserId();
    final int maxAttempts = 3;
    final long baseDelayMs = 10L; // 초기 백오프
    final Random random = new Random(); // 클래스 레벨에서 선언

    for (int attempt = 1; attempt <= maxAttempts; attempt++) { // try-with-resources 를 쓰는게 더 적합하지 않나? 아닌가./
      try {
        return doUseCouponOnce(couponId, userId);
      } catch (OptimisticLockingFailureException e) {
        // 충돌 : 백오프 후 재시도
        if (attempt == maxAttempts)  throw e; // 3번 모두 실패할 경우 예외 전파 ..

        long jitter = random.nextLong(baseDelayMs + 1); // 0 ~ baseDelayMs
        long backoff = (long) (baseDelayMs * Math.pow(2, attempt)) + jitter; // 지수 백오프

        try {
          Thread.sleep(backoff); // 스레드를 재우는 이유: 재시도 간의 지연을 위해
        } catch (InterruptedException e2) {
          Thread.currentThread().interrupt(); // 인터럽트 상태를 유지
          throw new IllegalStateException("Thread was interrupted 스레드 중단됨", e2); // InterruptedException: 스레드가 대기 중에 중단 신호를 받았을 때 발생하는 예외
        }
      }
    }
    throw new IllegalStateException("쿠폰 사용 재시도를 전부 실패했습니다.");
  }

  /**
   * 1회 소진 시도. 엔티티가 이미 미사용/만료를 자체 검증.
   * REQUIRES_NEW: 새로운 트랜잭션을 시작하여 독립적으로 실행.
   * protected: 서브클래스에서 호출 가능하며, 외부 접근은 제한.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected IssuedCoupon doUseCouponOnce(UUID couponId, UUID userId) {
    // 1. 상태와 무관하게 먼저 조회
    IssuedCoupon coupon = issuedCouponRepository
        .findByIdAndUserId(couponId, userId)
        .orElseThrow(() -> IssuedCouponException.notFound(couponId, userId));

    // 2. 도메인 객체가 상태 전이 규칙을 검증
    if(coupon.getStatus() != CouponUseStatus.ACTIVE){
      // 이미 사용된 경우 -> 명확한 409 Conflict
      throw IssuedCouponException.alreadyUsed(couponId);
    }

    // 3. 상태 변경 | 엔티티가 상태 전이 불변식(이미 사용/만료)인지 검증하는 로직
    coupon.useCoupon();

    // 🔁 이동: 내부(REQUIRES_NEW) 트랜잭션에서 발행 → AFTER_COMMIT 리스너는 이 커밋을 기준으로 실행
    eventPublisher.publishEvent(new CouponUsedEvent(coupon.getId(), userId));

    // 4. 저장 시 @Version 충돌 가능 (여기서 OptimisticLockingFailureException 유발)
    return issuedCouponRepository.save(coupon);
  }

  @Deprecated // 도메인에서 이미 검증하기 떄문에 서비스 계층에서 체크 불필요.
  private void checkIfCouponUsed(IssuedCoupon issuedCoupon) {
    if (issuedCoupon.isUsed()) {
      throw IssuedCouponException.alreadyUsed(issuedCoupon.getId()); // ✅
    }
  }

  @Deprecated // useCoupon()이 저장까지 처리.
  private IssuedCoupon processCouponUsage(IssuedCoupon issuedCoupon) {
    issuedCoupon.useCoupon();
    return issuedCouponRepository.save(issuedCoupon);
  }

  // ======================= 조회 =======================

  @Transactional(readOnly = true)
  public GetIssuedCouponResponse getIssuedCoupon(UUID couponId, UUID userId) {
    IssuedCoupon issuedCoupon = issuedCouponRepository
        .findByIdAndUserIdAndStatus(couponId, userId, CouponUseStatus.ACTIVE)
        .orElseThrow(() -> IssuedCouponException.notFound(couponId, userId));
    return GetIssuedCouponResponse.from(issuedCoupon);
  }

  @Transactional(readOnly = true)
  public IssuedCouponListResponse getIssuedCoupons(RequestUserDetails userDetails) {
    //TODO : 사용자한정
    List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findByUserId(userDetails.getUserId());
    return IssuedCouponListResponse.from(issuedCoupons);
  }

  // 첫 가입 쿠폰 발급 (샘플) (사실 이것도 발급으로 처리하는게 더 낫긴함...)
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

  public void issueFirstCouponDirectly(UUID userId) {
    String couponCode = "FIRST_COUPON";
    CouponPolicy couponPolicy = createFirstCouponPolicy(couponCode);

    IssuedCouponRequest request = new IssuedCouponRequest(couponCode);
    IssuedCoupon issuedCoupon = issueFirstCoupon(request, userId);
    FirstJoinCouponResponse.from(issuedCoupon);
  }


  public UsedIssuedCouponResponse useCouponAndPublishEvent(UUID couponId, RequestUserDetails userDetails) {
    IssuedCoupon issued = useCoupon(couponId, userDetails); // 재시도 포함
    return UsedIssuedCouponResponse.from(issued);
  }

  @Deprecated
  public void handleCouponUsedEvent(UUID couponId, UUID userId) {
    log.info("✅ 쿠폰 사용 후처리 시작: couponId={}, userId={}", couponId, userId);

    // 1) 미사용 쿠폰 조회 (Repository 직접 호출)
    IssuedCoupon issuedCoupon = issuedCouponRepository
        .findByIdAndUserIdAndStatus(couponId, userId, CouponUseStatus.ACTIVE)
        .orElseThrow(() -> IssuedCouponException.notFound(couponId, userId));
    checkIfCouponUsed(issuedCoupon);
    processCouponUsage(issuedCoupon);
  }
}