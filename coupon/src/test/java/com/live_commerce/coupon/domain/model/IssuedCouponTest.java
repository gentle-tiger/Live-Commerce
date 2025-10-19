package com.live_commerce.coupon.domain.model;

import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * IssuedCoupon 도메인 모델 테스트
 *
 * 📚 테스트 코드 작성 패턴:
 * 1. given (준비): 테스트에 필요한 데이터 준비
 * 2. when (실행): 테스트할 메서드 실행
 * 3. then (검증): 결과 확인
 */
class IssuedCouponTest {

    /**
     * 👨‍🏫 선생님 예시 1: 정상 케이스 테스트
     *
     * 📖 배울 점:
     * - 테스트 메서드 이름은 한글로 써도 됨 (실제로 추천!)
     * - @DisplayName으로 테스트 설명 추가
     * - given-when-then 패턴으로 구조화
     * - assertThat()으로 결과 검증
     */
    @Test
    @DisplayName("쿠폰 사용 성공 - ACTIVE 상태의 쿠폰을 사용하면 USED 상태로 변경")
    void useCoupon_Success() {
        // given (준비): 사용 가능한 쿠폰 생성
        IssuedCoupon coupon = IssuedCoupon.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .couponCode("WINTER_SALE")
                .isUsed(false)  // 아직 사용 안함
                .usedAt(null)   // 사용 시간 없음
                .expiresAt(LocalDateTime.now().plusDays(30))  // 30일 후 만료
                .build();

        // when (실행): 쿠폰 사용
        coupon.useCoupon();

        // then (검증): 결과 확인
        assertThat(coupon.isUsed()).isTrue();           // isUsed가 true로 변경
        assertThat(coupon.getStatus()).isEqualTo(CouponUseStatus.USED);  // 상태가 USED로 변경
        assertThat(coupon.getUsedAt()).isNotNull();     // 사용 시간이 기록됨
    }

    /**
     * 👨‍🏫 선생님 예시 2: 예외 케이스 테스트
     *
     * 📖 배울 점:
     * - assertThatThrownBy()로 예외 검증
     * - isInstanceOf()로 예외 타입 확인
     * - 예외가 발생해야 정상인 경우도 테스트!
     */
    @Test
    @DisplayName("이미 사용한 쿠폰을 다시 사용하면 예외 발생")
    void useCoupon_AlreadyUsed_ThrowsException() {
        // given: 이미 사용한 쿠폰 (USED 상태)
        IssuedCoupon coupon = IssuedCoupon.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .couponCode("WINTER_SALE")
                .isUsed(true)  // 이미 사용함!
                .usedAt(LocalDateTime.now().minusDays(1))  // 1일 전에 사용
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // 수동으로 status 설정 (builder에 없으므로)
        coupon.useCoupon();  // 첫 사용으로 USED 상태 만들기

        // when & then: 다시 사용 시도하면 예외 발생
        assertThatThrownBy(() -> coupon.useCoupon())
                .isInstanceOf(IssuedCouponException.class);  // 예외 타입 확인
    }

    /**
     * 🎓 학생 과제 1: 만료된 쿠폰 테스트
     *
     * 📝 TODO: 아래 테스트를 완성하세요!
     *
     * 힌트:
     * 1. expiresAt을 "과거 시간"으로 설정하세요
     * 2. useCoupon() 호출 시 IssuedCouponException이 발생해야 합니다
     * 3. 위의 "선생님 예시 2"를 참고하세요!
     */
    @Test
    @DisplayName("만료된 쿠폰을 사용하면 예외 발생")
    void useCoupon_Expired_ThrowsException() {
        // given: 만료된 쿠폰
        // TODO: 여기에 코드를 작성하세요
        IssuedCoupon coupon = IssuedCoupon.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .couponCode("WINTER_SALE")
            .isUsed(false)
            .usedAt(null) // 사용하지 않음.
            .expiresAt(LocalDateTime.now().minusDays(1))
            .build();

        // when & then: 사용 시도하면 예외 발생
        // TODO: assertThatThrownBy()를 사용하세요
        assertThatThrownBy(() -> coupon.useCoupon())
            .isInstanceOf(IssuedCouponException.class);

    }

    /**
     * 🎓 학생 과제 2: 쿠폰 만료 처리 테스트
     *
     * 📝 TODO: expireCoupon() 메서드 테스트를 작성하세요!
     *
     * 테스트 시나리오:
     * - ACTIVE 상태의 쿠폰을 만료 처리
     * - 만료 후 status가 EXPIRED로 변경되는지 확인
     *
     * 힌트:
     * 1. ACTIVE 상태의 쿠폰 생성
     * 2. expireCoupon(현재시간) 호출
     * 3. status가 EXPIRED인지 확인
     */
    @Test
    @DisplayName("ACTIVE 쿠폰을 만료 처리하면 EXPIRED 상태로 변경")
    void expireCoupon_Success() {
        // given: ACTIVE 상태의 쿠폰
        // TODO: 여기에 코드를 작성하세요
        IssuedCoupon coupon = IssuedCoupon.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .couponCode("WINTER_SALE")
            .isUsed(false)
            .status(CouponUseStatus.ACTIVE)
            .usedAt(null)
            .expiresAt(LocalDateTime.now().minusDays(1)) // 만료 시간을 과거로 설정
            .build();

        // when: 만료 처리
        // TODO: expireCoupon() 호출
        coupon.expireCoupon(LocalDateTime.now());

        // then: EXPIRED 상태로 변경 확인
        // TODO: assertThat()으로 검증
        assertThat(coupon.getStatus()).isEqualTo(CouponUseStatus.EXPIRED); // 만료 변경 여부 확인/.
        assertThat(coupon.isUsed()).isFalse(); // 만료 되었지만, 사용 안 함  (아직 레거시 중이기 때문에)
    }

    /**
     * 🎓 학생 과제 3: 이미 사용한 쿠폰은 만료 처리 안됨
     *
     * 📝 TODO: 테스트를 완성하세요!
     *
     * 테스트 시나리오:
     * - USED 상태의 쿠폰은 만료 처리해도 상태 변경 안됨
     * - expireCoupon() 호출 후에도 여전히 USED 상태
     */
    @Test
    @DisplayName("이미 사용한 쿠폰은 만료 처리해도 상태 변경 안됨")
    void expireCoupon_AlreadyUsed_NoChange() {
        // given: USED 상태의 쿠폰
        // TODO: 여기에 코드를 작성하세요
        IssuedCoupon coupon = IssuedCoupon.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .couponCode("WINTER_SALE")
            .isUsed(true)
            .status(CouponUseStatus.USED) // 사용 완료
            .usedAt(LocalDateTime.now().minusDays(1))
            .expiresAt(LocalDateTime.now().plusDays(30))
            .build();


        // when: 만료 처리 시도
        // TODO: expireCoupon() 호출
        coupon.expireCoupon(LocalDateTime.now()); // EXPIRED로 변경 시도

        // then: 여전히 USED 상태
        // TODO: status가 USED인지 확인
        assertThat(coupon.getStatus()).isEqualTo(CouponUseStatus.USED); // EXPIRED가 아니면 됨.

    }
}
