package com.live_commerce.coupon.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaContainerInspector {

  private final ObjectProvider<KafkaListenerEndpointRegistry> registryProvider;

  @EventListener(ApplicationReadyEvent.class)
  public void logAckModes() {
    var registry = registryProvider.getIfAvailable();
    if (registry == null) {
      log.warn("KafkaListenerEndpointRegistry not available. Is @EnableKafka present?");
      return;
    }
    registry.getListenerContainers().forEach(container -> {
      var c = (ConcurrentMessageListenerContainer<?, ?>) container;
      log.info("listenerId={} ackMode={}", container.getListenerId(), c.getContainerProperties().getAckMode());
    });
  }
}
