package com.live_commerce.coupon.infrastructure.kafka.publisher;

import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.infrastructure.kafka.message.CouponUsedMessage;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaCouponUsedEventPublisher implements PublishCouponUsedEventPort {

  private final KafkaTemplate<String, CouponUsedMessage> kafkaTemplate;

  // 이벤트 관련 상수 정의
  private static final String COUPON_USED_EVENT_TYPE = "CouponUsed";
  private static final int EVENT_VERSION = 1;  // 나중에 변경 가능

  @Value("${coupon.topics.coupon-used:coupons.CouponUsed.v1}")
  private String topic;

  @Override
  public void publishCouponUsedEvent(UUID couponId, UUID userId){
    Instant timestamp = Instant.now();

    var payload = new CouponUsedMessage(
        couponId,
        userId,
        timestamp,
        COUPON_USED_EVENT_TYPE,
        EVENT_VERSION
    );

    // 파티션 키를 userId로 할지 couponId로 할지: 중복 제거나 순서 요구에 맞춰 선택
    kafkaTemplate.send(topic, couponId.toString(), payload);
  }
}
