package com.live_commerce.coupon.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
public class IssuedCouponConcurrencyTest {

  @Autowired
  IssuedCouponService issuedCouponService;

  @Autowired
  IssuedCouponRepository issuedCouponRepository;

  @Test
  void concurrency_use_only_one_succeeds() throws Exception {
    // given : 쿠폰 1장 발급
    UUID userId = UUID.randomUUID();
    IssuedCoupon issuedCoupon = issuedCouponRepository.save(
        IssuedCoupon.builder()
            .userId(userId)
            .couponCode("WINTER_400")
            .isUsed(false)
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build());

    int threads = 20;
    ExecutorService es = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    AtomicInteger success = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();

    RequestUserDetails principal =
        new RequestUserDetails(userId, "tester", AuthorityUtils.NO_AUTHORITIES);

    // when
    for(int i = 0; i < threads; i++){
      es.submit(() -> {
        try{
          start.await();
          issuedCouponService.useCoupon(issuedCoupon.getId(), principal);
          success.incrementAndGet();
        }catch(Exception e){ // OptimisticLockingFailureException 등
          conflict.incrementAndGet();
        }finally{
          done.countDown();
        }
      });
    }
    start.countDown();
    done.await(); // Interceptter 에러 발생 가능
    es.shutdown();

    // then
    assertThat(success.get()).isEqualTo(1);
    assertThat(conflict.get()).isEqualTo(threads - 1);
  }
}
