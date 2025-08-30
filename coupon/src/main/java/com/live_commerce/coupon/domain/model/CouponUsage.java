package com.live_commerce.coupon.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "p_coupon_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponUsage extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private String couponCode;

  @Column(nullable = false)
  private BigDecimal amount;

  private CouponUseStatus status = CouponUseStatus.ACTIVE;

  @ManyToOne
  @JoinColumn(name = "coupon_id")
  private IssuedCoupon issuedCoupon;

  private boolean isUsed = false;

  private LocalDateTime usedAt;

}

