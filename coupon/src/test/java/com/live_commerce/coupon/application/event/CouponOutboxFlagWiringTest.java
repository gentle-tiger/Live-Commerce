package com.live_commerce.coupon.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.outbox.CouponOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 쿠폰 이벤트 발행 경로가 기능 플래그(coupon.outbox.enabled) 하나로 배타 선택되는지 검증한다.
 *
 * <p>배경: 두 핸들러가 같은 CouponUsedEvent를 구독한다.
 * <ul>
 *   <li>{@link CouponEventHandler} : AFTER_COMMIT 시점에 외부로 직접 발행(직발행 모드)</li>
 *   <li>{@link CouponEventOutboxHandler} : BEFORE_COMMIT 시점에 Outbox 테이블 적재(Outbox 모드)</li>
 * </ul>
 * 두 빈이 동시에 등록되면 쿠폰 1회 사용에 이벤트가 2번 나간다(직발행 1 + OutboxRelay 1).
 * 따라서 어떤 플래그 값에서도 "둘 중 정확히 하나"만 등록되어야 한다.
 *
 * <p>이 테스트는 DB/Kafka/Config Server 없이 조건부 빈 등록만 검증하기 위해
 * {@link ApplicationContextRunner}로 두 핸들러만 올린 슬라이스 컨텍스트를 사용한다.
 */
class CouponOutboxFlagWiringTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withBean(PublishCouponUsedEventPort.class, () -> mock(PublishCouponUsedEventPort.class))
      .withBean(CouponOutboxRepository.class, () -> mock(CouponOutboxRepository.class))
      .withBean(ObjectMapper.class, ObjectMapper::new)
      .withUserConfiguration(CouponEventHandler.class, CouponEventOutboxHandler.class);

  @Test
  @DisplayName("coupon.outbox.enabled=true → Outbox 핸들러만 등록된다(직발행 핸들러는 빠져야 한다)")
  void outboxMode_registersOnlyOutboxHandler() {
    contextRunner
        .withPropertyValues("coupon.outbox.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(CouponEventOutboxHandler.class);
          // 여기가 이중 발행의 핵심: 직발행 핸들러가 함께 살아 있으면 안 된다.
          assertThat(context).doesNotHaveBean(CouponEventHandler.class);
        });
  }

  @Test
  @DisplayName("coupon.outbox.enabled=false → 직발행 핸들러만 등록된다")
  void directMode_registersOnlyDirectHandler() {
    contextRunner
        .withPropertyValues("coupon.outbox.enabled=false")
        .run(context -> {
          assertThat(context).hasSingleBean(CouponEventHandler.class);
          assertThat(context).doesNotHaveBean(CouponEventOutboxHandler.class);
        });
  }

  @Test
  @DisplayName("플래그 미설정 → 기본값은 직발행 모드(Outbox 핸들러 미등록)")
  void flagMissing_fallsBackToDirectMode() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(CouponEventHandler.class);
      assertThat(context).doesNotHaveBean(CouponEventOutboxHandler.class);
    });
  }
}
