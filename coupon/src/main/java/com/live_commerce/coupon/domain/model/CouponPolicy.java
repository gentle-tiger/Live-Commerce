package com.live_commerce.coupon.domain.model;

import com.live_commerce.coupon.domain.exception.CouponDiscountTypeException;
import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.presentation.dto.request.UpdateCouponPolicyRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Table(name = "p_coupon_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponPolicy extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DISCOUNT_TYPE discountType;

  @Column(nullable = false)
  private BigDecimal discountValue;

  @Column(nullable = false)
  private BigDecimal minOrderAmt;

  private BigDecimal maxOrderAmt;

  @Column(nullable = false)
  private LocalDateTime startAt;

  @Column(nullable = false)
  private LocalDateTime endAt;

  private boolean isActive;

  @Builder
  public CouponPolicy(String code, String name, DISCOUNT_TYPE discountType, BigDecimal discountValue,
                      BigDecimal minOrderAmt, BigDecimal maxOrderAmt,
                      LocalDateTime startAt, LocalDateTime endAt, boolean isActive) {

    if (code == null || name == null || discountType == null || discountValue == null
        || minOrderAmt == null || startAt == null || endAt == null) {
      throw new IllegalArgumentException("CouponPolicy의 필수 값이 null일 수 없습니다.");
    }

    this.code = code;
    this.name = name;
    this.discountType = discountType;
    this.discountValue = discountValue;
    this.minOrderAmt = minOrderAmt ;
    this.maxOrderAmt = maxOrderAmt;
    this.startAt = startAt;
    this.endAt = endAt;
    this.isActive = isActive;

    validateInvariants(); // 생성 시 불변식 보장.
  }

  public void update(UpdateCouponPolicyRequest req){
    String newName = req.name();
    DISCOUNT_TYPE newType = req.discountType();
    BigDecimal newDiscountValue = req.discountValue();
    BigDecimal newMin = req.minOrderAmt();
    BigDecimal newMax = req.maxOrderAmt();
    LocalDateTime newStart = req.startAt();
    LocalDateTime newEnd = req.endAt();
    boolean newActive = req.isActive();

    // 임시로 필드 바꾸지 말고, 체크만
    // 쿠폰 시작 시간이 누락됐을 때 에러 던짐
    if(newStart.isAfter(newEnd)) throw CouponPolicyException.invalidDateRange();
    // 젇액할인일 때, 최대값이 null이면 안 되고, 할인금액이 0보다 커야함.
    if(newType == DISCOUNT_TYPE.FIXED && newMax != null && newDiscountValue.compareTo(newMax)> 0) // 근데 왜 compareTo를 쓰고, 0이랑 비교하지..?
      throw CouponPolicyException.discountGreaterThanMaxOrderAmount();
    // 정률할인일 때, 최대값이 100이 ..??
    if(newType == DISCOUNT_TYPE.RATE && newDiscountValue.compareTo(BigDecimal.valueOf(100)) > 0)
      throw CouponPolicyException.discountGreaterThan100();

    // 사실 위에 검증 로직 삭제하고 validateInveriants에서 검증하면 되는거 아닌가?
    validateInvariants();

    // 검증이 끝나면 반영
    this.name = newName;
    this.discountType = newType;
    this.discountValue = newDiscountValue;
    this.minOrderAmt = newMin;
    this.maxOrderAmt = newMax;
    this.startAt = newStart;
    this.endAt = newEnd;
    this.isActive = newActive;

  }



  /** 엔티티 스스로 지키는 순수 도메인 규칙(저장소/I-O 비의존) */
  private void validateInvariants(){

    if(startAt.isAfter(endAt)){
      throw CouponPolicyException.invalidDateRange();
    }
    if(discountType == DISCOUNT_TYPE.FIXED){
      // 고정 금액 할인은 최대주문금액 상한을 넘을 수 없음
      if(maxOrderAmt != null && discountValue.compareTo(maxOrderAmt) > 0){ // BigDecimal은 객체 타입으로 같은 원치 타입 비교시 사용
        // 현재 값이 인자보다 작으면 -1 반환
        // 현재 값이 인자보다 같으면 0 반환
        // 현재 값이 인자보다 크면 1 반환
        throw CouponPolicyException.discountGreaterThanMaxOrderAmount();
      }
      // (권장) 0원 초과 여부 등 하한 검증은 필요 시 코드/에러코드 추가
    }else if(discountType == DISCOUNT_TYPE.RATE){
      // 비율 할인은 100% 초과 금지 (하한 검증 필요 시 추가)
      if(discountValue.compareTo(BigDecimal.valueOf(100)) > 0){
        throw CouponPolicyException.discountGreaterThan100();
      }
    }
  }

  public void markCouponAsDeleted(String deletedBy){
    markAsDeleted(deletedBy);
  }
}