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
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(name ="coupon.outbox.enabled", havingValue = "true") // 기능 플래그로 켜고 끄기
public class OutboxRelay {

  private final CouponOutboxRepository outboxRepo;
  private final PublishCouponUsedEventPort publisher;  // 외부 시스템(예: Kafka 등)으로 퍼블리시
  private final ObjectMapper objectMapper;
  private final Timer publishTimer;
  private final Counter publishFailCounter;

  public OutboxRelay(CouponOutboxRepository outboxRepo,
      PublishCouponUsedEventPort publisher,
      MeterRegistry registry,
      ObjectMapper objectMapper) {
    this.outboxRepo = outboxRepo;
    this.publisher = publisher;
    this.objectMapper = objectMapper;

    // 한 번 run()이 수행되는 데 걸린 시간
    this.publishTimer = Timer.builder("outbox_publish_duration")
        .description("Duration of outbox publish runs")
        .register(registry);

    // 실패 건수 누적
    this.publishFailCounter = Counter.builder("outbox_publish_failed_total")
        .description("Number of failed publish attempts in outbox relay")
        .register(registry);

    // 큐 크기(전체 행) 게이지
    Gauge.builder("outbox_rows_total", outboxRepo::count)
        .description("Total number of rows in outbox (all statuses)")
        .register(registry);
  }

  @Scheduled(fixedDelayString = "PT5S") // 이전 실행 종료 후 5초 간격으로 반복
  @SchedulerLock(name = "coupon-outbox-relay", lockAtMostFor = "PT1M", lockAtLeastFor = "PT3S")
  public void run(){
    publishTimer.record(() -> { // 전체 틱 실행시간 계측
      int processed = 0;
      while(true){
        int n = processBatch(200); // 배치 단위로 처리
         processed += n;
         if(n < 200) break; // 더 이상 처리할 게 없으면 종료
      }
      log.debug("Outbox relay tick processed {}", processed);
    });
  }

  @Transactional // 배치 1회가 트랜잭션 경계
  public int processBatch(int limit){
    var rows = outboxRepo.lockNextBatch(limit);  // 처리 대상 선점(락)
    for(var row : rows){
      try{
        var node = objectMapper.readTree(row.getPayload()); // payload 파싱
        if("CouponUsedEvent".equals(row.getEventType())){
          var couponId = UUID.fromString(node.get("couponId").asText());
          var userId   = UUID.fromString(node.get("userId").asText());
          publisher.publishCouponUsedEvent(couponId, userId); // 외부 발행
        }else{
          log.warn("Unhandled eventType {}", row.getEventType());
        }
        row.setStatus(OutboxStatus.SENT);          // 성공→ SENT
        row.setPublishedAt(LocalDateTime.now());   // 발행 시각 기록
      }catch (Exception e){
        row.setAttempts(row.getAttempts() + 1);    // 시도 횟수 증가
        row.setStatus(row.getAttempts() >= 10 ? OutboxStatus.FAILED : OutboxStatus.PENDING); // 재시도 or 실패 확정
        row.setErrorMessage(truncate(e.getMessage(), 1000)); // 에러 메시지 저장(최대 1000자)
        publishFailCounter.increment();            // 실패 지표++
        log.error("Outbox publish failed id={}, attempts={}", row.getId(), row.getAttempts(), e);
      }
    }
    return rows.size();
  }

  private static String truncate(String s,int n){
    if(s == null) return null;
    return s.length() <= n ? s : s.substring(0, n);
  }

}
