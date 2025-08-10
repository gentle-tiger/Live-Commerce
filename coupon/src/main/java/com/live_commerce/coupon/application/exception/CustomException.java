package com.live_commerce.coupon.application.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

  private final ExceptionCode exceptionCode;
  private final Object[] messageArgs; // 메시지 템플릿 파라미터

  // 메시지는 넣지 않음 → 핸들러에서 조립
  public CustomException(ExceptionCode exceptionCode, Object... messageArgs) {
    super();
    this.exceptionCode = exceptionCode;
    this.messageArgs = messageArgs;
  }

  public CustomException(ExceptionCode exceptionCode, Throwable cause, Object... messageArgs) {
    super(cause);
    this.exceptionCode = exceptionCode;
    this.messageArgs = messageArgs;
  }

  // 로그 등에서 null이 보이지 않도록 messageKey를 fallback으로 노출(선택)
  @Override
  public String getMessage() {
    // 로깅 시 NPE 방지용 최소한의 정보만 (i18n은 핸들러에서 처리)
    return exceptionCode != null ? exceptionCode.getMessageKey() : super.getMessage();
  }
}
