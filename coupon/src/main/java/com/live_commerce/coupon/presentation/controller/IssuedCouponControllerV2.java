package com.live_commerce.coupon.presentation.controller;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.infrastructure.common.ResponseUtil;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.common.ApiResponse;
import com.live_commerce.coupon.presentation.dto.response.UsedIssuedCouponResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/issued-coupons")
@RequiredArgsConstructor
public class IssuedCouponControllerV2 {

  private final IssuedCouponService issuedCouponService;


  /**
   *  쿠폰 사용 요쳥(kafka)
   */

  @PatchMapping("/{couponId}/use")
  public ResponseEntity<ApiResponse<UsedIssuedCouponResponse>> useCoupon(
      @PathVariable UUID couponId,
      @AuthenticationPrincipal RequestUserDetails userDetails) {
    UsedIssuedCouponResponse response =
        issuedCouponService.useCouponAndPublishEvent(couponId, userDetails);  // 컨트롤러는 변경 X. 내부에서 재시도+이벤트
    return ResponseUtil.success(response);
  }


  /**
   * 첫 회원가입 쿠폰 발급(kafka)
   * @param userId
   * @return
   */

  @PostMapping("/{userId}/signup-first")
  public ResponseEntity<ApiResponse<Void>> signupFirstCoupon(@PathVariable UUID userId) {
    issuedCouponService.issueFirstCouponDirectly(userId);
    return ResponseUtil.noContent();
  }
}
