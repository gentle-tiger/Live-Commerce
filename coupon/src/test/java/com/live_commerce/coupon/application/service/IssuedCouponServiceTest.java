package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.CouponUseStatus;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.CouponPolicyRepository;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import com.live_commerce.coupon.presentation.dto.response.GetIssuedCouponResponse;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * IssuedCouponService 테스트
 * <p>
 * 📚 서비스 계층 테스트 패턴: 1. @Mock: 가짜 객체 생성 (Repository, EventPublisher 등) 2. @InjectMocks: Mock을 주입받을
 * 실제 테스트 대상 (Service) 3. given().willReturn(): Mock 동작 정의 4. verify(): Mock 메서드 호출 확인
 * <p>
 * 💡 도메인 테스트와의 차이: - 도메인: 순수 객체만 테스트 (의존성 없음) - 서비스: Repository 등 의존성을 Mock으로 대체
 * <p>
 * ⚠️ 주의사항: - 아래 선생님 예시를 그대로 믿지 말고 한번 검토해보세요! - RequestUserDetails 생성 방법이 맞는지 확인 필요 - Mock 사용법이 올바른지
 * 점검 필요
 */
@ExtendWith(MockitoExtension.class)  // Mockito 사용을 위한 확장
class IssuedCouponServiceTest {

  @Mock
  private IssuedCouponRepository issuedCouponRepository;  // 가짜 Repository

  @Mock
  private CouponPolicyRepository couponPolicyRepository;  // 가짜 Repository

  @Mock
  private ApplicationEventPublisher eventPublisher;  // 가짜 EventPublisher

  @InjectMocks
  private IssuedCouponService issuedCouponService;  // 실제 Service (Mock들이 주입됨)

  /**
   * 👨‍🏫 선생님 예시 1: 쿠폰 발급 성공 케이스
   * <p>
   * ⚠️ 검토 포인트: 1. RequestUserDetails 생성 방법이 맞는가? - RequestUserDetails.java를 확인해보세요 (77번 라인) - 생성자
   * 파라미터가 올바른가요? 2. Mock 객체들이 제대로 설정되었는가? 3. 검증(assertion)이 충분한가?
   * <p>
   * 📝 TODO: 아래 코드를 검토하고 잘못된 부분을 수정하세요!
   */
  @Test
  @DisplayName("쿠폰 발급 성공 - CouponPolicy 존재 시 IssuedCoupon 생성")
  void issueCoupon_Success() {
    // given: 테스트 데이터 준비
    String couponCode = "WINTER_SALE";
    UUID userId = UUID.randomUUID();

    // 1️⃣ 쿠폰 발급 요청 DTO 생성 (컨트롤러에서 받는 요청 데이터)
    IssuedCouponRequest request = new IssuedCouponRequest(couponCode);

    // 2️⃣ 사용자 인증 정보 생성 (로그인한 사용자 정보)
    // Collection<? extends GrantedAuthority> 타입 = 사용자의 권한 목록
    RequestUserDetails userDetails = new RequestUserDetails(
        userId,
        "테스트 유저",
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_MASTER"))
    );

    // 3️⃣ Mock 쿠폰 정책 객체 생성
    // "DB에 이런 정책이 있다고 가정"하는 가짜 데이터
    // 실제 DB에 저장하지 않고, 테스트용으로만 메모리에 생성
    CouponPolicy mockPolicy = CouponPolicy.builder()
        .code(couponCode)
        .name("WINTER_SALE")
        .discountType(DISCOUNT_TYPE.FIXED)
        .discountValue(BigDecimal.valueOf(10000))
        .minOrderAmt(BigDecimal.valueOf(0))
        .maxOrderAmt(BigDecimal.valueOf(100000))
        .startAt(LocalDateTime.now())
        .endAt(LocalDateTime.now().plusMonths(1))
        .isActive(true)
        .build();

    // 4️⃣ Mock 발급 쿠폰 객체 생성
    // "DB에 저장되고 반환될 쿠폰"을 미리 만들어둔 가짜 데이터
    IssuedCoupon coupon = IssuedCoupon.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .couponCode(couponCode)
        .isUsed(false)
        .status(CouponUseStatus.ACTIVE)
        .usedAt(null)
        .expiresAt(mockPolicy.getEndAt())
        .build();

    // 5️⃣ Mock 동작 정의 (given-willReturn)
    // 실제 서비스가 "Repository를 호출하면 어떤 값을 반환할지" 미리 정의

    // 시나리오: "couponCode로 정책 조회하면 mockPolicy를 반환해줘"
    given(couponPolicyRepository.findByCodeAndDeletedStatusFalse(couponCode))
        .willReturn(Optional.of(mockPolicy));

    // 시나리오: "쿠폰을 저장하면 coupon을 반환해줘"
    given(issuedCouponRepository.save(any(IssuedCoupon.class)))
        .willReturn(coupon);

    // 6️⃣ when: 실제 서비스 메서드 실행
    // 이 시점에 위에서 정의한 Mock이 동작함!
    IssuedCoupon result = issuedCouponService.issueCoupon(request, userDetails);

    // 7️⃣ then: 결과 검증 (반환값이 예상대로인지 확인)
    assertThat(result).isNotNull(); // 쿠폰이 발급되었는가?
    assertThat(result.getCouponCode()).isEqualTo(couponCode); // 쿠폰 코드가 일치하는가?
    assertThat(result.getUserId()).isEqualTo(userId); // 사용자 ID가 일치하는가?
    assertThat(result.isUsed()).isFalse(); // 아직 사용하지 않은 상태인가?
    assertThat(result.getStatus()).isEqualTo(CouponUseStatus.ACTIVE); // ACTIVE 상태인가?

    // 8️⃣ verify: Mock 메서드 호출 검증
    // "서비스가 실제로 Repository를 호출했는가?" 확인
    // 단순히 결과만 맞으면 안되고, 올바른 절차를 거쳤는지도 중요!
    verify(couponPolicyRepository).findByCodeAndDeletedStatusFalse(couponCode); // 정책 조회 했나?
    verify(issuedCouponRepository).save(any(IssuedCoupon.class)); // 쿠폰 저장 했나?
  }

  /**
   * 👨‍🏫 선생님 예시 2: 쿠폰 조회 예외 케이스
   * <p>
   * 📖 배울 점: 1. given().willReturn(Optional.empty()) - 데이터가 없는 경우 2. assertThatThrownBy() - 서비스에서도
   * 예외 검증 가능 3. verify() - Mock 호출 확인
   * <p>
   * ⚠️ 검토 포인트: 1. 이 예시는 RequestUserDetails가 필요 없어서 동작합니다 2. 하지만 코드 로직이 올바른지 한번 검토해보세요 3. 더 좋은 검증
   * 방법은 없을까요?
   */
  @Test
  @DisplayName("쿠폰 조회 실패 - 존재하지 않는 쿠폰 ID로 조회 시 예외 발생")
  void getIssuedCoupon_NotFound_ThrowsException() {
    // given: 존재하지 않는 쿠폰 ID
    UUID couponId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // 🎯 given: Repository에서 빈 결과 반환 (쿠폰이 없음)
    given(
        issuedCouponRepository.findByIdAndUserIdAndStatus(couponId, userId, CouponUseStatus.ACTIVE))
        .willReturn(Optional.empty());

    // when & then: 예외 발생 확인
    assertThatThrownBy(() -> issuedCouponService.getIssuedCoupon(couponId, userId))
        .isInstanceOf(IssuedCouponException.class);

    // 🎯 verify: Repository 메서드가 호출되었는지 확인
    verify(issuedCouponRepository).findByIdAndUserIdAndStatus(couponId, userId,
        CouponUseStatus.ACTIVE);
  }

  /**
   * 🎓 학생 과제 1: 쿠폰 발급 실패 - CouponPolicy가 없는 경우
   * <p>
   * 📝 TODO: 테스트를 완성하세요!
   * <p>
   * 힌트: 1. RequestUserDetails는 사용하지 않아도 됩니다 (userDetails 파라미터만 필요하면 됨) 2.
   * couponPolicyRepository.findByCodeAndDeletedStatusFalse() 호출 시 Optional.empty() 반환 3.
   * issuedCouponService.issueCoupon() 호출 시 CouponPolicyException 발생 확인 4. verify()로
   * couponPolicyRepository가 호출되었는지 확인
   */
  @Test
  @DisplayName("쿠폰 발급 실패 - 존재하지 않는 CouponCode로 발급 시 예외 발생")
  void issueCoupon_PolicyNotFound_ThrowsException() {
    // given: 존재하지 않는 쿠폰 코드
    // TODO: 여기에 코드를 작성하세요
    String couponCode = "NONEXISTENT_COUPON";
    UUID userId = UUID.randomUUID();

    // 요청 데이터 생성
    IssuedCouponRequest request = new IssuedCouponRequest(couponCode);

    // 사용자 정보 생성
    RequestUserDetails userDetails = new RequestUserDetails(
        userId,
        "테스트 유저",
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_MASTER"))
    );

    // 🎯 given: Mock 동작 정의 - Repository에서 빈 결과 반환
    // TODO: couponPolicyRepository.findByCodeAndDeletedStatusFalse() -> Optional.empty()
    given(couponPolicyRepository.findByCodeAndDeletedStatusFalse(couponCode))
        .willReturn(Optional.empty()); // 쿠폰 정책이 없음 !

    // when & then: 예외 발생 확인
    // TODO: assertThatThrownBy()를 사용하세요
    assertThatThrownBy(() -> issuedCouponService.issueCoupon(request, userDetails));

    // 🎯 verify: Repository 호출 확인
    // TODO: verify()를 사용하세요
    verify(couponPolicyRepository).findByCodeAndDeletedStatusFalse(couponCode);

  }

  /**
   * 🎓 학생 과제 2: 쿠폰 조회 성공 케이스
   * <p>
   * 📝 TODO: 테스트를 완성하세요!
   * <p>
   * 힌트: 1. Mock IssuedCoupon 생성 (선생님 예시 1의 93-101번 라인 참고) 2.
   * issuedCouponRepository.findByIdAndUserIdAndStatus() -> Optional.of(mockCoupon) 3.
   * issuedCouponService.getIssuedCoupon() 호출 4. 결과 검증 (GetIssuedCouponResponse 타입)
   */
  @Test
  @DisplayName("쿠폰 조회 성공 - ACTIVE 상태 쿠폰 조회 시 정보 반환")
  void getIssuedCoupon_Success() {
    // given: ACTIVE 상태의 쿠폰
    // TODO: couponId, userId 생성
    UUID couponId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String couponCode = "WINTER_SALE";
    // TODO: Mock IssuedCoupon 생성 (선생님 예시 1 참고)
    IssuedCoupon coupon = IssuedCoupon.builder()
        .id(couponId)
        .userId(userId)
        .couponCode(couponCode)
        .isUsed(false)
        .status(CouponUseStatus.ACTIVE) // 사용 가능 상태
        .usedAt(null)
        .expiresAt(LocalDateTime.now().plusMonths(1))
        .build();

    // 🎯 given: Mock 동작 정의
    // TODO: issuedCouponRepository.findByIdAndUserIdAndStatus() -> Optional.of(mockCoupon)
    given(
        issuedCouponRepository.findByIdAndUserIdAndStatus(couponId, userId, CouponUseStatus.ACTIVE))
        .willReturn(Optional.of(coupon));

    // when: 서비스 메서드 호출
    // TODO: issuedCouponService.getIssuedCoupon() 호출
    // given(issuedCouponService.getIssuedCoupon(couponId, userId)); -> given 은 Mock 객체에만 사용한다. issuedCouponService는 실제 테스트 대상이기에 given을 쓰면 안 됨.
    GetIssuedCouponResponse result = issuedCouponService.getIssuedCoupon(couponId, userId);


    // then: 결과 검증
    // TODO: assertThat()으로 검증 (result != null, couponCode 확인 등)
    assertThat(result).isNotNull();
    assertThat(result.couponCode()).isEqualTo(couponCode);

    // 🎯 verify: Repository 호출 확인
    // TODO: verify()를 사용하세요
    verify(issuedCouponRepository).findByIdAndUserIdAndStatus(couponId, userId,
        CouponUseStatus.ACTIVE);

  }

  /**
   * 🎓 학생 과제 3: 사용자 쿠폰 목록 조회
   * <p>
   * 📝 TODO: getIssuedCoupons() 메서드 테스트를 작성하세요!
   * <p>
   * 테스트 시나리오: - 사용자가 발급받은 쿠폰 목록 조회 - Repository에서 여러 개의 쿠폰 반환 - IssuedCouponListResponse 타입으로 반환
   * <p>
   * 힌트: 1. RequestUserDetails 생성 필요 (선생님 예시 1의 TODO 부분 먼저 해결하세요!) 2. Mock IssuedCoupon 여러 개 생성
   * (List.of(...)) 3. issuedCouponRepository.findByUserId() -> List 반환 4. 결과 검증 (리스트 크기 확인)
   */
  @Test
  @DisplayName("사용자 쿠폰 목록 조회 - 여러 개의 쿠폰 반환")
  void getIssuedCoupons_Success() {
    // given: 사용자 정보와 여러 개의 쿠폰
    // TODO: RequestUserDetails 생성 (선생님 예시 1 참고)
    UUID userId = UUID.randomUUID();
    RequestUserDetails userDetails = new RequestUserDetails(userId, "테스트 유저", Collections.singletonList(new SimpleGrantedAuthority("ROLE_MASTER")));
    // TODO: Mock IssuedCoupon 2-3개 생성 후 List로 만들기
    List<IssuedCoupon> coupons = List.of(
        IssuedCoupon.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .couponCode("WINTER_SALE")
            .isUsed(false)
            .status(CouponUseStatus.ACTIVE)
            .usedAt(null)
            .expiresAt(LocalDateTime.now().plusMonths(1))
            .build(),
        IssuedCoupon.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .couponCode("SUMMER_SALE")
            .isUsed(false)
            .status(CouponUseStatus.ACTIVE)
            .usedAt(null)
            .expiresAt(LocalDateTime.now().plusMonths(1))
            .build()
    );


    // 🎯 given: Mock 동작 정의
    // TODO: issuedCouponRepository.findByUserId() -> 쿠폰 리스트 반환
    given(issuedCouponRepository.findByUserId(userId)).willReturn(coupons);

    // when: 서비스 메서드 호출
    // TODO: issuedCouponService.getIssuedCoupons() 호출
    issuedCouponService.getIssuedCoupons(userDetails);

    // then: 결과 검증
    // TODO: 리스트가 null이 아닌지, 크기가 맞는지 확인
    assertThat(coupons).isNotEmpty();
    assertThat(coupons.size()).isEqualTo(2);

    // 🎯 verify: Repository 호출 확인
    // TODO: verify()를 사용하세요
    verify(issuedCouponRepository).findByUserId(userId);
  }
}
