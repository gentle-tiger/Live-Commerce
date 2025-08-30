package com.live_commerce.coupon.application.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.live_commerce.coupon.application.port.out.PublishCouponUsedEventPort;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.domain.repository.IssuedCouponRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public class CouponEventAfterCommitTest {

  @MockitoBean // 실제 전송을 막는 거?? ㅋ
  PublishCouponUsedEventPort port;

  @Autowired
  IssuedCouponService couponService;

  @Autowired
  IssuedCouponRepository couponRepository;

  @Test
  void publish_after_commit_only(){
    UUID userId = UUID.randomUUID();
    IssuedCoupon issued = couponRepository.save(
        IssuedCoupon.builder()
            .userId(userId)
            .couponCode("SPRING_COUPON_999")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build()
    );

    RequestUserDetails principal = new RequestUserDetails(userId, "tester", AuthorityUtils.NO_AUTHORITIES);

    couponService.useCouponAndPublishEvent(issued.getId(), principal); // 실제 비즈니스 로직 실행

    // 키멋 후 호출(트랜잭션 종료 시점)만 검증: 포트 호출 1회
    verify(port, times(1)).publishCouponUsedEvent(eq(issued.getId()), eq(userId));
  }
}
