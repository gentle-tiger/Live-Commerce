package com.live_commerce.coupon.application.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IssuedCouponExceptionCode implements ExceptionCode {

  COUPON_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "제공된 쿠폰 코드에 대한 쿠폰 정책을 찾을 수 없습니다."),
  COUPON_ALREADY_ISSUED(HttpStatus.BAD_REQUEST, "이미 사용자에게 쿠폰이 발곱되었습니다."),
  ISSUED_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "발급된 쿠폰이 없습니다."),
  ISSUED_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 사용한 쿠폰입니다."),
  COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "쿠폰이 만료되었습니다."),
  INVALID_COUPON_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 쿠폰 코드입니다."),
  COUPON_CONDITION_NOT_MET(HttpStatus.BAD_REQUEST, "쿠폰 사용 조건이 충족되지 않았습니다."),
  MAX_COUPON_LIMIT_REACHED(HttpStatus.BAD_REQUEST, "쿠폰 발급 한도를 초과했습니다."),
  COUPON_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "해당 쿠폰은 사용자가 사용할 수 없습니다.");


  private final HttpStatus httpStatus;
  private final String messageKey;

}