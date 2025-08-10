package com.live_commerce.coupon.application.exception;


import org.springframework.http.HttpStatus;

public interface ExceptionCode {
  HttpStatus getHttpStatus();
  String getMessageKey();
  String name(); // enum 식별자 노출(로그/응답 코드용)
}