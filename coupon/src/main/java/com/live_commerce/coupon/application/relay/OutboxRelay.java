package com.live_commerce.coupon.application.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.outbox.CouponOutboxRepository;
import com.live_commerce.coupon.domain.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(name ="coupon.outbox.enabled", havingValue = "true")
public class OutboxRelay {

  private final CouponOutboxRepository outboxRepo;
  private final PublishCouponUsedEventPort publisher;
  private final MeterRegistry registry;
  private final Timer publishTimer;

  public OutboxRelay(CouponOutboxRepository outboxRepo, PublishCouponUsedEventPort publisher,
                     MeterRegistry registry) {
    this.outboxRepo = outboxRepo;
    this.publisher = publisher;
    this.registry = registry;
    this.publishTimer = Timer.builder("outbox_publish_diration")
        .description("Duration of outbox publish runs")
        .register(registry);

    Gauge.builder("outbox_pending", () -> outboxRepo.count())
        .description("Number of outbox rows (all status)")
        .register(registry);
  }

  @Scheduled(fixedDelayString = "PT5S")
  @SchedulerLock(name = "coupon-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT3S")
  public void run(){
    publishTimer.record(() -> {
      int processed = 0;
      while(true){
        int n = processBatch(200);
         processed += n;
         if(n < 200) break;
      }
      log.debug("Outbox relay tick processed {}", processed);
    });
  }

  @Transactional
  public int processBatch(int limit){
    var rows = outboxRepo.lockNextBatch(limit);
    var mapper = new ObjectMapper();
    for(var row : rows){
      try{
        var node = mapper.readTree(row.getPayload());
        if("CouponUsedEvent".equals(row.getEventType())){
          var couponId = UUID.fromString(node.get("couponId").asText());
          var userId   = UUID.fromString(node.get("userId").asText());
          publisher.publishCouponUsedEvent(couponId, userId);
        }else{
          log.warn("Unhandled eventType {}", row.getEventType());
        }
        row.setStatus(OutboxStatus.SENT);
        row.setPublishedAt(LocalDateTime.now());
      }catch (Exception e){
        row.setAttempts(row.getAttempts() + 1);
        row.setStatus(row.getAttempts() >= 10 ? OutboxStatus.FAILED : OutboxStatus.PENDING);
        row.setErrorMessage(truncate(e.getMessage(), 1000));
        log.error("Outbox publish failed id={}, attempts={}, err={}", row.getId(), row.getAttempts(), e.toString());
      }
    }
    return rows.size();
  }

  private static String truncate(String s,int n){
    if(s == null) return null;
    return s.length() <= n ? s : s.substring(0, n);
  }

}
