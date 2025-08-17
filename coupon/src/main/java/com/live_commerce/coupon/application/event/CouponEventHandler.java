package com.live_commerce.coupon.application.event;

import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/* DB 커밋 이후에만 외부 발행 (AFTER_COMMIT) */
@ConditionalOnBean(PublishCouponUsedEventPort.class)
@Component
@RequiredArgsConstructor
public class CouponEventHandler {

  private final PublishCouponUsedEventPort publishCouponUsedEventPort; // port로 외부 발행

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCouponUsed(CouponUsedEvent event){
    publishCouponUsedEventPort.publishCouponUsedEvent(event.couponId(), event.userId());
  }
}
