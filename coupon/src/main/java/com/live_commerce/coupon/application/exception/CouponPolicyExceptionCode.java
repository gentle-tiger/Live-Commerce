package com.live_commerce.coupon.application.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponPolicyExceptionCode implements ExceptionCode {

  MISSING_MAX_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "정률 할인 유형에는 최대 주문 금액이 필수입니다."),
  MISSING_MIN_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "고정 할인 유형에는 최소 주문 금액이 필수입니다."),
  DUPLICATE_COUPON_CODE(HttpStatus.BAD_REQUEST, "이미 존재하는 쿠폰명입니다."),
  INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일은 종료일보다 이전이어야 합니다."),
  DISCOUNT_GREATER_THAN_MAX_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "할인 금액이 최대 주문 금액을 초과할 수 없습니다."),
  DISCOUNT_GREATER_THAN_100(HttpStatus.BAD_REQUEST, "정률 할인 비율은 100을 넘을 수 없습니다."),
  COUPON_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰 정책이 없거나 모두 삭제되었습니다.");

  private final HttpStatus httpStatus;
  private final String messageKey;

}