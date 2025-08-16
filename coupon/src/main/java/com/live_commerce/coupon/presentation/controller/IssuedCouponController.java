package com.live_commerce.coupon.presentation.controller;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.domain.model.IssuedCoupon;
import com.live_commerce.coupon.infrastructure.common.ResponseUtil;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.common.ApiResponse;
import com.live_commerce.coupon.presentation.dto.request.IssuedCouponRequest;
import com.live_commerce.coupon.presentation.dto.response.FirstJoinCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.GetIssuedCouponResponse;
import com.live_commerce.coupon.presentation.dto.response.IssuedCouponListResponse;
import com.live_commerce.coupon.presentation.dto.response.UsedIssuedCouponResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/issued-coupons")
@RequiredArgsConstructor
public class IssuedCouponController {

  private final IssuedCouponService issuedCouponService;

  /**
   * 쿠폰 발급
   * @param request
   * @param userDetails
   * @return
   */
  @PostMapping("/")
  public ResponseEntity<ApiResponse<IssuedCoupon>> issueCoupon(
      @RequestBody IssuedCouponRequest request,
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    IssuedCoupon response = issuedCouponService.issueCoupon(request, userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 쿠폰 사용 처리
   * @param couponId
   * @param userDetails
   * @return
   */
  @Deprecated
  @PatchMapping("/{couponId}/use")
  public ResponseEntity<ApiResponse<UsedIssuedCouponResponse>> useCoupon(
      @PathVariable UUID couponId,
      @AuthenticationPrincipal RequestUserDetails userDetails) {
    IssuedCoupon issuedCoupon = issuedCouponService.useCoupon(couponId, userDetails);
    UsedIssuedCouponResponse response = UsedIssuedCouponResponse.from(issuedCoupon);
    return ResponseUtil.success(response);
  }

  /**
   * 단일 쿠폰 조회
   * @param couponId
   * @param userDetails
   * @return
   */
  @GetMapping("/{couponId}")
  public ResponseEntity<ApiResponse<GetIssuedCouponResponse>> getIssuedCoupon(
      @PathVariable UUID couponId,
      @AuthenticationPrincipal RequestUserDetails userDetails) {
    GetIssuedCouponResponse response = issuedCouponService.getIssuedCoupon(couponId, userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 쿠폰 목록 조회
   * @param userDetails
   * @return
   */
  @GetMapping("/")
  public ResponseEntity<ApiResponse<IssuedCouponListResponse>> getIssuedCoupons(
      @AuthenticationPrincipal RequestUserDetails userDetails) {
    IssuedCouponListResponse response = issuedCouponService.getIssuedCoupons(userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 첫 회원가입 쿠폰 발급
   * @param userId
   * @return
   */
  @Deprecated
  @PostMapping("/{userId}/signup-first")
  public ResponseEntity<ApiResponse<FirstJoinCouponResponse>> issueFirstCoupon(
      @PathVariable UUID userId
  ) {
    FirstJoinCouponResponse response = issuedCouponService.issueFirstCoupon(userId);
    return ResponseUtil.success(response);
  }
}
