package com.live_commerce.coupon.domain.model;

import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.*;

@Entity
@Getter
@Table(name = "p_issued_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, updatable = false)
  private String couponCode;

  private boolean isUsed;

  private LocalDateTime usedAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Version
  private Long version;

  @Builder
  public IssuedCoupon(UUID id, UUID userId, String couponCode, boolean isUsed, LocalDateTime usedAt,
      LocalDateTime expiresAt) {
    this.id = id;
    this.userId = userId;
    this.couponCode = couponCode;
    this.isUsed = isUsed;
    this.usedAt = usedAt;
    this.expiresAt = expiresAt;
  }

  public static IssuedCoupon from(IssuedCouponRequest request,
      CouponPolicy policy, UUID userId) {
    return IssuedCoupon.builder()
        .userId(userId)
        .couponCode(request.couponCode())
        .isUsed(false)
        .usedAt(null)
        .expiresAt(policy.getEndAt())
        .build();
  }

  // 엔티티가 자신의 유효 상태를 스스로 보장(DDD 기본 원칙)
  public void useCoupon() {
    // 쿠폰 사용 여부 검증
    if (this.isUsed) {
      throw IssuedCouponException.alreadyUsed(this.id); // 409
    }
    LocalDateTime now = LocalDateTime.now();
    // 쿠폰 만료 시간 검증
    if(this.expiresAt.isBefore(now)){
      throw IssuedCouponException.expired(this.id, this.userId); // 410
    }
    this.isUsed = true;
    this.usedAt =  now;
  }

}
