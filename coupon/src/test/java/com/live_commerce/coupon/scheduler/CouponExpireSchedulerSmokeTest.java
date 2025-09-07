package com.live_commerce.coupon.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.live_commerce.coupon.application.scheduler.CouponExpireScheduler;
import com.live_commerce.coupon.domain.model.CouponUseStatus;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CouponExpireSchedulerSmokeTest {

  @Autowired
  IssuedCouponRepository repo;

  @Autowired
  CouponExpireScheduler scheduler; // 스케줄 메서드 직접 호출

  /**
   * 목적:
   *  - 만료 스케줄러가 "현재 시각 기준 만료된 ACTIVE 쿠폰만" 정확히 EXPIRED로 변경하는지 검증한다.
   *  - 동일 작업을 재실행해도 결과가 달라지지 않는지(멱등성)까지 확인한다.
   *
   * 전략:
   *  1) 테스트 픽스처 생성:
   *     - (만료 대상) ACTIVE & expiresAt < now 인 쿠폰 N개
   *     - (유지 대상) ACTIVE & expiresAt > now 인 쿠폰 M개
   *  2) 스케줄러 메서드 직접 호출: scheduler.expireActiveCoupons()
   *  3) then:
   *     - 만료 대상만 EXPIRED, 유지 대상은 ACTIVE 그대로
   *     - 스케줄러 재실행해도 상태 변화 없음(멱등)
   *
   * 참고:
   *  - 실제 @Scheduled 트리거는 사용하지 않고 메서드 직접 호출로 검증한다.
   *  - 엔티티가 기본 status를 보장하지 않는다면 테스트에서 ACTIVE 로 세팅(아래 setActive).
   *  - 외부 Config/Eureka 등 MSA 의존으로 컨텍스트 로딩이 깨진다면 @SpringBootTest(properties=...)로 끄는 것을 권장.
   */
  @Test
  @DisplayName("만료 스케줄러: ACTIVE 중 만료시각이 지난 쿠폰만 EXPIRED로 변경되고, 재실행해도 변화 없다(멱등)")
  void expire_bulk_updates_and_idempotent() {
    // given: 만료 대상 3개(과거), 유지 대상 2개(미래)
    LocalDateTime now = LocalDateTime.now();
    var expiredTargets = createActive(repo, now.minusMinutes(5), 3);  // expiresAt < now
    var stillActives = createActive(repo, now.plusMinutes(10), 2);    // expiresAt > now

    // when: 만료 스케줄 실행
    scheduler.expireActiveCoupons();
    // then: 만료 대상만 EXPIRED인지 확인
    assertAllStatus(expiredTargets, CouponUseStatus.EXPIRED);
    assertAllStatus(stillActives, CouponUseStatus.ACTIVE);

    // when(멱등성): 한 번 더 실행
    scheduler.expireActiveCoupons();

    // then(멱등성): 상태 변화 없음
    assertAllStatus(expiredTargets, CouponUseStatus.EXPIRED);
    assertAllStatus(stillActives, CouponUseStatus.ACTIVE);
  }

  // ----------------헬퍼 메서드 -----------------

  /**
   * ACTIVE 쿠폰 n개 생성/저장 (status=ACTIVE, isUsed=false, usedAt=null)
   * - 만료 시각(expiresAt)과 개수(n)를 받아 동일 조건의 쿠폰들을 대량 생성한다.
   * - 스케줄러가 만료시킨 후 상태 전이(EXPIRED) 여부를 검증하기 위한 픽스처이다.
   */
  private List<IssuedCoupon> createActive(IssuedCouponRepository repo, LocalDateTime expiresAt, int n){
    List<IssuedCoupon> saved = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      var c = IssuedCoupon.builder()
          .userId(UUID.randomUUID())
          .couponCode("EXPIRE_TEST_" + i + "_" + expiresAt.toLocalTime())
          .isUsed(false)
          .usedAt(null)
          .expiresAt(expiresAt)
          .build();
      // 엔티티가 기본 status를 보장하지 않는다면 테스트에서 ACTIVE 주입
      // (도메인에 IssuedCoupon.active(...) 팩토리 제공 시 그걸 쓰면 더 깔끔)
      setActive(c);
      saved.add(repo.save(c));
    }
    return saved;
  }
  /**
   * 주어진 쿠폰 목록이 기대 상태로 영속화되었는지 재조회하여 모두 검증한다.
   * - EXPIRED 검증 시 isUsed 호환 필드(false)까지 함께 확인.
   */
  private void assertAllStatus(List<IssuedCoupon> coupons, CouponUseStatus expected) {
    for(var c : coupons){
      var reloaded = repo.findById(c.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(expected);

      // 호환 필드가 있으면 추가 확인
      if(expected == CouponUseStatus.EXPIRED){
        assertThat(reloaded.isUsed()).isFalse();
      }
    }
  }
  /**
   * 테스트 편의를 위한 상태 주입(Reflection).
   * - 운영 코드 대신 테스트에서만 사용. 도메인 기본값(@PrePersist)이나 팩토리 메서드가 있으면 대체 가능.
   */
  private void setActive(IssuedCoupon c){
    try{
      var f = IssuedCoupon.class.getDeclaredField("status");
      f.setAccessible(true);
      f.set(c, CouponUseStatus.ACTIVE);
    }catch(Exception e){
      throw new IllegalStateException(e);

    }
  }
}
