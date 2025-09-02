package com.live_commerce.coupon.application.scheduler;

import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.netflix.discovery.converters.Auto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireScheduler {

  private final IssuedCouponRepository repo;
  private final Counter expiredCounter;

  @Autowired
  public CouponExpireScheduler(MeterRegistry registry, IssuedCouponRepository repo){
    this.repo = repo;
    this.expiredCounter = Counter.builder("expired_coupons_total")
        .description("스케줄러에 의해 만료된 쿠폰의 총 수")
        .register(registry);
  }

  @Scheduled(cron = "0 0/10 * * * *") // 10분마다
  @SchedulerLock(name = "expire-active-coupons", lockAtMostFor= "PT5M", lockAtLeastFor = "PT30S")
  @Transactional
  public void expireActiveCoupons(){ // "활성화된 쿠폰을 만료시킨다"이기에 expireSoonCoupons(곧 만료될 쿠폰을 만료시킨다) 보다 지금이 더 낫다.
    int updated = repo.bulkExpireActiveBeforeNow();
    if(updated > 0) expiredCounter.increment(updated);
    // 사용자 알림/메트릭 적재 등 처리
    log.info("✅ 만료 마킹 완료: {} rows", updated);
  }
}
