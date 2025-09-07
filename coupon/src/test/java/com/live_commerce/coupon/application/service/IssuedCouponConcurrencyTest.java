package com.live_commerce.coupon.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import java.time.LocalDateTime;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.AuthorityUtils;

@SpringBootTest
public class IssuedCouponConcurrencyTest {

  @Autowired
  IssuedCouponService issuedCouponService;

  @Autowired
  IssuedCouponRepository issuedCouponRepository;

  @Test
  void concurrency_use_only_one_succeeds() throws Exception {
    // given : 쿠폰 1장 발급
    UUID userId = UUID.randomUUID();
    IssuedCoupon issuedCoupon = issuedCouponRepository.save(
        IssuedCoupon.builder()
            .userId(userId)
            .couponCode("WINTER_400")
            .isUsed(false)
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build());

    // ---- 동시성 테스트 파라미터 ----
    final int THREADS = 20;
    final int TIMEOUT_SEC = 10;

    // 스래드 풀: 동시에 달려들도록 고정 크기 풀을 사용
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    // start: 모든 작업자 스레드를 "대기"시켰다가, 한 번에 출발시키는 신호탄(=게이트)
    CountDownLatch start = new CountDownLatch(1);

    // done: 모든 작업자 스레드가 일을 마칠 때까지 메인 스레드가 "대기"하기 위한 카운터
    CountDownLatch done = new CountDownLatch(THREADS);

    // success: 실제 사용(useCoupon)이 정상적으로 1번만 성공했는지 집계하는 카운터
    AtomicInteger success = new AtomicInteger();

    // conflict: 낙관적 락 출동/중복 사용 방지로 실패한 횟수 집계 (정답은 THREADS - 1)
    AtomicInteger conflict = new AtomicInteger();

    // 디버깅용: 발생 예외를 모아두기 (필수는 아님, 테스트 실패 시 원인 파악에 도움)
    Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

    // principal: 인증된 사용자 시뮬레이션(서비스가 소유자 검증 등을 한다면 필요)
    RequestUserDetails principal =
        new RequestUserDetails(userId, "tester", AuthorityUtils.NO_AUTHORITIES);

    // when: 20개의 작업자가 동시에 같은 쿠폰을 사용하려고 시도
    for(int i = 0; i < THREADS; i++){
      pool.submit(() -> {
        try{
          start.await(); // 출발 신호가 떨어질 떄까지 모든 스레드가 여기서 "대기"
          issuedCouponService.useCoupon(issuedCoupon.getId(), principal); // 실제 비즈니스 호출
          success.incrementAndGet(); // OptimisticLockingFailureException 등 모든 예외 집계
        }catch(Exception e){ // OptimisticLockingFailureException 등
          conflict.incrementAndGet(); // 실패(충돌)로 집계
        }finally{
          done.countDown(); // 이 스레드의 작업 종료를 알림.
        }
      });
    }
    start.countDown(); // "모두 출발!" - 대기 중이던 20개의 작업자가 동시에 달려듦
    boolean finished = done.await(TIMEOUT_SEC, TimeUnit.SECONDS); // 지정된 시간 내 모두 종료 대기(데드락 걸려도 지정 시간 동안은 테스트가 멈추지 않음)
    pool.shutdown();
    pool.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS);

    // then: 기대값 검증
    assertThat(finished).isTrue();                             // 타임아웃 없이 모두 종료되었는지
    assertThat(success.get()).isEqualTo(1);            // 정확히 1건만 성공
    assertThat(conflict.get()).isEqualTo(THREADS - 1); // 나머지는 모두 충돌(실패)

    // 최종 상태 검증: DB에 영속 반영이 정상인지 확인
    IssuedCoupon  reloaded = issuedCouponRepository.findById(issuedCoupon.getId()).orElseThrow();
    assertThat(reloaded.isUsed()).isTrue();
    assertThat(reloaded.getExpiresAt()).isAfter(LocalDateTime.now());
  }


//  @Test
//  void bulkExpire_clearsPersistenceContext(){
//    // given: EXPIRED 대상이 될 ACTIVE 쿠폰 하나 저장
//    let c = issuedCouponRepository.save(fixtureActiveExpiredNow());
//  }
}