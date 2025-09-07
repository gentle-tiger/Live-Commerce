package com.live_commerce.coupon.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.model.CouponUseStatus;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@DisplayName("동시성: 쿠폰 사용은 정확히 1회 성공하고 이벤트 발행은 1회만 호출된다")
public class IssuedCouponUseExactlyOnceE2ETest {

  @Autowired
  IssuedCouponService service;

  @Autowired
  IssuedCouponRepository repo;

  @MockitoBean
  PublishCouponUsedEventPort publisher;


  /**
   * 목적:
   *  - 동시 다발적인 쿠폰 사용 요청에도 도메인/서비스가 "정확히 1회만 사용 성공"을 보장하는지 검증한다.
   *  - 그 결과로 외부 발행 포트(PublishCouponUsedEventPort) 호출도 "정확히 1회"인지 확인한다.
   *
   * 전략:
   *  - ACTIVE 쿠폰 1장을 DB에 저장한 뒤, 20개 스레드에서 동시에 useCoupon 호출.
   *  - 완료 후 상태는 USED 여야 하며, 이벤트 발행은 1회만 발생해야 한다.
   *
   * 비고:
   *  - 서비스 내부가 @Async 이벤트를 사용한다면 verify(timeout) 값 또는 Awaitility 사용을 고려.
   *  - MSA 외부 의존(Config/Eureka 등)로 인한 ApplicationContext 로딩 실패를 막으려면
   *    위의 @SpringBootTest(properties=...) 주석을 해제하거나 test 프로필에서 끄는 것을 권장.
   */
  @Test
  void only_one_success_and_one_publish() throws Exception {
    // given: ACTIVE 쿠폰 1장 (DB에 저장)
    UUID userId = UUID.randomUUID();
    IssuedCoupon coupon = IssuedCoupon.builder()
        .userId(userId)
        .couponCode("WINTER_COUPON_200")
        .isUsed(false)
        .usedAt(null)
        .expiresAt(LocalDateTime.now().plusHours(1))
        .build();
    // 엔티티에 setter/factory가 없다면 테스트에서 상태를 주입
    ReflectionTestUtils.setField(coupon, "status", CouponUseStatus.ACTIVE);
    coupon = repo.save(coupon); // @GeneratedValue(id) 채움

    final UUID couponId = coupon.getId();

    // 인증 주체(요청 사용자) Mock: 서비스 시그니처가 RequestUserDetails를 요구
    RequestUserDetails principal = mock(RequestUserDetails.class);
    when(principal.getUserId()).thenReturn(userId);

    // 동시성 시나리오: 스레드풀 + 래치로 "동시에 출발" 보장
    int threads = 20;
    var pool = Executors.newFixedThreadPool(threads);
    var startGate = new CountDownLatch(1); // 출발 신호
    var doneGate = new CountDownLatch(threads); // 완료 대기

    for (int i = 0; i < threads; i++) {
      pool.execute(() -> {
        try {
          startGate.await();                      // 동시에 출발
          service.useCoupon(couponId, principal);
        } catch (Exception ignored) {
        } finally {
          doneGate.countDown();                   // 스레드 완료
        }
      });
    }

    startGate.countDown();  // 모둔 작업자 출발
    doneGate.await(5, TimeUnit.SECONDS); // 5초 대기
    pool.shutdown();
    pool.awaitTermination(2, TimeUnit.SECONDS);

    // then: 상태는 USED 여야 함(정확히 1회만 성공했음을 의미)
    var used = repo.findById(coupon.getId()).orElseThrow();
    assertThat(used.getStatus()).isEqualTo(CouponUseStatus.USED);

    // 그리고 외부 발행은 정확히 1회만 호출되어야 함(인자 매칭 포함)
    verify(publisher, timeout(2000).times(1))
        .publishCouponUsedEvent(eq(couponId), eq(userId));
    verifyNoMoreInteractions(publisher);
  }
}
