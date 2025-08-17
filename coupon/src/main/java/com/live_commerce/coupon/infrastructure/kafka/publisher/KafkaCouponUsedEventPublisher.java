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

  @Value("${coupon.topics.coupon-used:coupons.CouponUsed.v1}")
  private String topic;

  /**
   *  이벤트 스키마 버전 상수로 관리하거나 payload 안에 명시하세요.
   *  항상 타입을 동일하게 하는게 맞나? 버전은 항상 1로 고정시켜두나?
   * public final class EventMeta {
   *   private EventMeta() {}
   *   public static final String COUPON_USED_EVENT_TYPE = "CouponUsed";
   *   public static final int COUPON_USED_EVENT_VERSION = 1;
   * }
   */
  @Override
  public void publishCouponUsedEvent(UUID couponId, UUID userId){
    var payload = new CouponUsedMessage(
        couponId,
        userId,
        Instant.now(), // 인스턴스 now는 뭘까? -> 서버 UTC 타임스탬프
        "CouponUsed",
        1 // 버전을 직접 명시하는게 맞나? -> 이벤트 스키마 버전(상수로 관리 권장)
    );
    kafkaTemplate.send(topic, couponId.toString(), payload);
  }
}
