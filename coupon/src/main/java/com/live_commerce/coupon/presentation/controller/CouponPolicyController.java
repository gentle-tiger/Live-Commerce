package com.live_commerce.coupon.presentation.controller;

import com.live_commerce.coupon.application.service.CouponPolicyService;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.infrastructure.common.ResponseUtil;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.common.ApiResponse;
import com.live_commerce.coupon.presentation.dto.request.CreateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.request.UpdateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.response.CreateCouponPolicyResponse;
import com.live_commerce.coupon.presentation.dto.response.ReadCouponPolicyResponse;
import com.live_commerce.coupon.presentation.dto.response.SearchCouponPolicyResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupon-policies")
@RequiredArgsConstructor
public class CouponPolicyController {

  private final CouponPolicyService couponService;

  /**
   * 쿠폰 정책 리스트 조회
   * @param userDetails
   * @return
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<ReadCouponPolicyResponse>>> getCouponPolicies(
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    List<ReadCouponPolicyResponse> response = couponService.getCouponPolicies(userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 단일 쿠폰 정책 조회
   * @param code
   * @param userDetails
   * @return
   */
  @GetMapping("/detail/{code}")
  public ResponseEntity<ApiResponse<ReadCouponPolicyResponse>> getCouponPolicy(
      @PathVariable String code,
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    ReadCouponPolicyResponse response = couponService.getCouponPolicy(code, userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 쿠폰 정책 검색
   * @param keyword
   * @param page
   * @param sortBy
   * @param discountType
   * @param userDetails
   * @return
   */
  @GetMapping("/search")
  public ResponseEntity<ApiResponse<SearchCouponPolicyResponse>> searchCouponPolicy(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "asc") String sortBy,
      @RequestParam(defaultValue = "FIXED") DISCOUNT_TYPE discountType,
      @AuthenticationPrincipal RequestUserDetails userDetails

  ) {
    SearchCouponPolicyResponse response = couponService.searchCouponPolicy(keyword, page, sortBy,
        discountType, userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 쿠폰 정책 생성
   * @param request
   * @param userDetails
   * @return
   */
  @PostMapping
  public ResponseEntity<ApiResponse<CreateCouponPolicyResponse>> createCouponPolicy(
      @Valid @RequestBody CreateCouponPolicyRequest request,
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    CreateCouponPolicyResponse response = couponService.createCouponPolicy(request, userDetails);
    return ResponseUtil.success(response);
  }

  /**
   * 쿠폰 정책 삭제
   * @param code
   * @param userDetails
   * @return
   */
  @DeleteMapping("/{code}")
  public ResponseEntity<ApiResponse<Void>> deleteCouponPolicy(
      @PathVariable String code,
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    couponService.deleteCouponPolicy(code, userDetails);
    return ResponseUtil.noContent();
  }

  /**
   * 쿠폰 정책 수정
   * @param code
   * @param request
   * @param userDetails
   * @return
   */
  @PatchMapping("/{code}")
  public ResponseEntity<ApiResponse<Void>> updateCouponPolicy(
      @PathVariable String code,
      @RequestBody UpdateCouponPolicyRequest request,
      @AuthenticationPrincipal RequestUserDetails userDetails
  ) {
    couponService.updateCouponPolicy(code, request, userDetails);
    return ResponseUtil.noContent();
  }



}