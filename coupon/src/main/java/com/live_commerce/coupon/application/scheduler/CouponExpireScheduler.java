package com.live_commerce.coupon.application.scheduler;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireScheduler {

  private final IssuedCouponService repo;

  @Scheduled(cron = "0 0/10 * * * *") // 10분마다
  public void reportSoonExpiring(){
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime warn = now.plusDays(1);

    // 필요 시 repo에 쿼리 추가: isUsed=false AND expiresAt BETWEEN now AND warn
    // 사용자 알림/메트릭 적재 등 처리
    log.info("만료 임박 쿠폰 점검 now={} ~ warn={}", now, warn);  }
}
