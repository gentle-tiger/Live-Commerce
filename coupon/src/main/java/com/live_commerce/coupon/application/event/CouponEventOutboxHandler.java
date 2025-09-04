package com.live_commerce.coupon.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import com.live_commerce.coupon.domain.outbox.CouponOutbox;
import com.live_commerce.coupon.domain.outbox.CouponOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CouponUsedEvent 발생 시점에 Outbox 테이블에 이벤트를 저장하는 핸들러
 * 트랜잭션 커밋 직전에 실행되며, 이벤트를 JSON으로 직렬화 해 repo에 저장.
 * 이후 Ouybox 테이블을 별도 프로세스/메시지 브로커가 읽어 다른 서비스로 안전하게 전달함.
 * "이벤트를 잃지 않고 외부 시스템에 전달하기 위한 중간 저장소 역할"
 *
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.outbox.enabled", havingValue = "true")
public class CouponEventOutboxHandler {

  private final CouponOutboxRepository outboxRepo;
  private final ObjectMapper objMapper;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onCouponUsedToOutbox(CouponUsedEvent event) {
    try {
      String payload = objMapper.writeValueAsString(event); // 이벤트 데이터 자체
      String headers = "{}"; // 메타데이터. 예) 이벤트 설명

      var row = CouponOutbox.pending(
          "IssuedCoupon",
          event.couponId().toString(),
          "CouponUsedEvent",
          payload,
          headers,
          event.userId().toString()
      );
      outboxRepo.save(row);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
