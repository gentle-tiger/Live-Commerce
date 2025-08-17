package com.live_commerce.coupon.infrastructure.kafka.producer;

import com.live_commerce.coupon.infrastructure.kafka.message.CouponUsedMessage;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaProducerConfig {

  // kafkaProperties란??
  // KafkaProperties는 Spring Boot가 spring.kafka.* yml 설정을 묶어주는 바인딩 객체입니다.
  // 이 클래스를 주입받으면 yml에 적은 부트스트랩 서버, 직렬화기, 재시도 등 설정이 props.buildProducerProperties()에 담겨옵니다.
  // spring.kafka.* 가 application.yml에 있으면
  // Spring Boot가 KafkaProperties로 바인딩해줍니다.
  @Bean
  public ProducerFactory<String, CouponUsedMessage> couponUsedProducerFactory(KafkaProperties props) {
    // yml의 producer 설정을 그대로 가져와 타입만 맞춰 factory 생성
    return new DefaultKafkaProducerFactory<>(props.buildProducerProperties());
  }

  @Bean
  public KafkaTemplate<String, CouponUsedMessage> couponUsedKafkaTemplate(
      ProducerFactory<String, CouponUsedMessage> pf){
    return new KafkaTemplate<>(pf);
  }
}
