package com.live_commerce.coupon.infrastructure.kafka.consumer;

import com.live_commerce.coupon.domain.event.CouponUsedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
//@RequiredArgsConstructor
public class CouponUsedEventConsumer {

  /**
   * Kafka 소비자에서 내부 상태를 다시 바꾸는 코드 제거하거나 격리해야함.
   * 이미 소진 처리 후(handleCouponUsedEvent) 이벤트를 발행하는데(log.info)
   * 같은 서비스가 그 이벤트를 다시 소비해 중복 소진 위험이 있음.
   */
//  private final IssuedCouponService issuedCouponService; // 그래서 주석함. 이건 여기서 사용할 게 아니었음,

  @KafkaListener(
          id = "coupon-used-listener",
          topics = "${coupon.topics.coupon-used:coupons.CouponUsed.v1}",
          groupId = "${spring.kafka.consumer.group-id}"
      // containerFactory 지정 불필요함 :  기본 팩토리 사용 + yml 의 ask-mode=MANUAL_IMMEDIATE 적용 )
  )
  public void onMessage(
      @Payload CouponUsedEvent event,
      @Header(KafkaHeaders.RECEIVED_KEY) String key,
      Acknowledgment ack
  ){
    try{
      // 1) 비즈니스 처리 (예 : 로그/DB 갱신)
      log.info("Consumed coupon-used key={}, event={}", key, event);

      // 2) 처리 성공 시 바로 커밋
      ack.acknowledge();  // MANUAL_IMMEDIATE: 즉시 동기 커밋

    }catch (Exception e){
      // 예외 발생 시 커밋하지 않음 -> 재처리(재시도/에러핸들러 정책에 따름)
      log.error("Failed to process coupon-used key={}", key, e);
      // 필요하면 여기서 Dead Letter/ 알림 등 처리
    }
  }

}