package com.live_commerce.coupon.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * 스케줄러 활성화 + ShedLock 분산락으로 클러스터에서 잡을 단 1회만 실행하도록 보장.
 * 다중 인스턴스·중복 실행이 위험한 배치(예: 쿠폰 만료, Outbox 릴레이)에 적합.
 * 단일 인스턴스·멱등 잡·외부 오케스트레이션(Quartz/K8s)로 동시실행 금지 보장 시는 불필요.
 * 각 잡에 @SchedulerLock을 지정하고 lockAtMostFor/lockAtLeastFor 를 작업 특성에 맞게 튜닝.
 * usingDbTime()으로 DB 시간 기준 TTL을 사용해 now() 경계/시계 드리프트 오차를 줄임.
 */

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime() // db 서버 시간을 기준으로 락 만료를 계산 (노드 간 시계 드리프트 문제 방지)
            .build()
    );
  }
}