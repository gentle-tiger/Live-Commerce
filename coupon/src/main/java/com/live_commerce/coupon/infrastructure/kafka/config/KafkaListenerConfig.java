package com.live_commerce.coupon.infrastructure.kafka.config;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaListenerConfig {

  /** 재시도 3회, 1초 간격. 실패 시 커밋 없이 그대로 두면 재소비(또는 별도 DLT 전략 추가 가능) */
  @Bean
  public DefaultErrorHandler defaultErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(1000L, 3));
  }

  /**
   * 기본 팩토리 이름을 사용하면(@Bean name = "kafkaListenerContainerFactory" 생략 시),
   * @KafkaListener 가 별도 containerFactory 지정 없이도 이 팩토리를 씁니다.
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
      ConsumerFactory<String, Object> consumerFactory,
      CommonErrorHandler errorHandler,
      KafkaProperties props
  ) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
    factory.setConsumerFactory(consumerFactory);

    // yml의 ack-mode=MANUAL_IMMEDIATE를 그대로 쓰고 싶으면 이 줄은 생략 가능.
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

    factory.setCommonErrorHandler(errorHandler);
    // 필요 시 동시성 등 추가 설정 가능: factory.setConcurrency(3);
    return factory;
  }
}
