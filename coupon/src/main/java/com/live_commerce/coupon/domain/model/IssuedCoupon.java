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

  @Column(name = "is_used", nullable = false) /** 레거시 호환 필드 (이후 제거 예정) */
  private boolean isUsed;

  private LocalDateTime usedAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CouponUseStatus status = CouponUseStatus.ACTIVE; /** 추가 */

  @Version
  private Long version;

  @PrePersist
  void prePersist() {
    if(status == null) {
      status = CouponUseStatus.ACTIVE;
    }
  }
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
    // (1) 이미 사용
    if (status == CouponUseStatus.USED) {
      throw IssuedCouponException.alreadyUsed(id); // 409
    }
    LocalDateTime now = LocalDateTime.now();
    // (2) 만료됨: SQL에서 <= now()를 쓰므로 자바도 동일 의미로
    if(status == CouponUseStatus.EXPIRED || !expiresAt.isAfter(now)){
      throw IssuedCouponException.expired(id, userId); // 410
    }

    // (3) 상태 전이: ACTIVE -> USED
    this.status = CouponUseStatus.USED;
    this.isUsed = true;  // 호환(과도기)
    this.usedAt =  now;
  }

  public void expireCoupon(LocalDateTime now){
    if(status == CouponUseStatus.ACTIVE && expiresAt.isAfter(now)){ // !expiresAt.isAfter(now)를 쓰면 서버/배치 결과가 일치
      this.status = CouponUseStatus.EXPIRED;
      this.isUsed = false; // 호환(USED가 아니므로 false로 변환)
    }
  }
}
