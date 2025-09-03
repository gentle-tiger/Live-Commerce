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

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "coupon.outbox.enabled", havingValue = "true")
public class CouponEventOutboxHandler {

  private final CouponOutboxRepository outboxRepo;
  private final ObjectMapper objMapper;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onCouponUsedToOutbox(CouponUsedEvent event) {
    try {
      String payload = objMapper.writeValueAsString(event);
      String headers = "{}";

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
