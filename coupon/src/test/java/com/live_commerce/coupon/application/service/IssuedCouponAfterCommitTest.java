package com.live_commerce.coupon.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@DisplayName("쿠폰 사용 이벤트 발행: 커밋 시점에서만 외부 포트 호출")
public class IssuedCouponAfterCommitTest {

  @Autowired
  TransactionTemplate tt;

  // 실제 빈을 Spy로 감시하여 호출 여부/횟수/인자 검증
  @MockitoSpyBean
  PublishCouponUsedEventPort publisher;

  @Autowired
  ApplicationEventPublisher eventPublisher;

  /**
   * 목적:
   *  - 트랜잭션이 "커밋"될 때만 @TransactionalEventListener(AFTER_COMMIT)가 동작하여
   *    PublishCouponUsedEventPort 가 호출되는지 검증한다.
   *
   * 전제:
   *  - CouponUsedEvent 를 수신하여 AFTER_COMMIT 시점에 publisher.publishCouponUsedEvent(...)를
   *    호출하는 이벤트 리스너가 애플리케이션 컨텍스트에 등록되어 있어야 한다.
   */
  @Test
  @DisplayName("롤백에서는 0회, 커밋에서는 정확히 1회 호출된다")
  void publish_occurs_only_after_commit() {
    UUID couponId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // when: (1) 롤백 트랜잭션 안에서 이벤트 발행
    // 기대: @DisplayName("롤백에서는 0회, 커밋에서는 정확히 1회 호출된다")
    try{
      tt.executeWithoutResult(status -> {
        eventPublisher.publishEvent(new CouponUsedEvent(couponId, userId));
        status.setRollbackOnly(); // 롤백 마크 → 커밋 훅이 실행되지 않음
      });
    }catch(Exception e){}

    // then: 짧은 대기(timeout 200ms) 동안 호출이 없었는지 확인
    verify(publisher, timeout(200).times(0))
        .publishCouponUsedEvent(any(),any());


    // when: (2) 커밋 트랜잭션 안에서 이벤트 발행
    // 기대: 커밋 완료 시 AFTER_COMMIT 리스너가 동작하여 외부 호트 1회 호출
    tt.executeWithoutResult(status ->{
      eventPublisher.publishEvent(new CouponUsedEvent(couponId, userId));
    });

    // then: 커밋 훅 실행 여지를 주기 위해 timeout(1000ms) 내 1회, 정확한 파라미터로 호출되었는지 검증
    verify(publisher, timeout(1000).times(1))
        .publishCouponUsedEvent(couponId, userId);
  }


}
