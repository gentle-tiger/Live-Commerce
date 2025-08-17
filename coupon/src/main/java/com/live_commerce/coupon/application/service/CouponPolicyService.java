package com.live_commerce.coupon.application.service;

import com.live_commerce.coupon.application.exception.CouponPolicyExceptionCode;
import org.jetbrains.annotations.Contract;
import org.springframework.security.access.AccessDeniedException;
import com.live_commerce.coupon.application.validation.CouponPolicyValidator;
import com.live_commerce.coupon.domain.exception.CouponPolicyException;
import com.live_commerce.coupon.domain.model.CouponPolicy;
import com.live_commerce.coupon.domain.model.DISCOUNT_TYPE;
import com.live_commerce.coupon.domain.repository.CouponPolicyRepository;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.request.CouponPolicySearchResult;
import com.live_commerce.coupon.presentation.dto.response.SearchCouponPolicyResponse;
import com.live_commerce.coupon.presentation.dto.request.CreateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.request.UpdateCouponPolicyRequest;
import com.live_commerce.coupon.presentation.dto.response.CreateCouponPolicyResponse;
import com.live_commerce.coupon.presentation.dto.response.ReadCouponPolicyResponse;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Transactional
public class CouponPolicyService {

  private static final String ROLE_MASTER = "ROLE_MASTER";
  private final CouponPolicyRepository couponPolicyRepository;
  private final CouponPolicyValidator couponPolicyValidator;

  public CreateCouponPolicyResponse createCouponPolicy(CreateCouponPolicyRequest request,
      RequestUserDetails user) {

    requireMaster(user); // 가드 한 줄로 권한 처리 가능함 .
    couponPolicyValidator.validateForCreatePolicy(request);

    CouponPolicy couponPolicy = request.toCouponPolicy();
    couponPolicyRepository.save(couponPolicy);
    return CreateCouponPolicyResponse.fromCouponPolicy(couponPolicy);
  }

  @Contract("null -> false")
  private boolean hasRoleMaster(RequestUserDetails user) {
    if (user == null) {
      return false; // 정책: 미인증/누락일 경우 false
    }
    var authorities = user.getAuthorities();
    if (authorities == null || authorities.isEmpty()) {
      return false; // null에 대해 방어적 가드
    }

    return authorities.stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(ROLE_MASTER::equals); /// equals : 객체 내용 비교
  }
  // anyMatch(P) : 스트림 요소 중 하나라도 조건 P를 만족하면 true : "있다"는 빠른 판단
  // noneMatch(P) : 스트림 요소 중 어떤 것도 P 조건을 만족하지 않으면 true : "없다"는 빠른 판단
  // 동치관계 : noneMatch(P) == !anyMatch(P)

  private void requireMaster(RequestUserDetails user) {
    if (!hasRoleMaster(user)) {
      throw new AccessDeniedException("마스터 권한이 있는 유저만 생성할 수 있습니다.");
    }
  }

  @Transactional(readOnly = true)
  public ReadCouponPolicyResponse getCouponPolicy(String code, RequestUserDetails user) {
    requireMaster(user);

    CouponPolicy couponPolicy = findCouponPolicyOrElseThrow(code);
    return ReadCouponPolicyResponse.fromCouponPolicy(couponPolicy);
  }

  private CouponPolicy findCouponPolicyOrElseThrow(String code) {
    return couponPolicyRepository.findByCodeAndDeletedStatusFalse(code)
        .orElseThrow(() -> CouponPolicyException.notFound(code));
  }

  @Transactional(readOnly = true)
  public List<ReadCouponPolicyResponse> getCouponPolicies(RequestUserDetails user) {
    requireMaster(user);
    List<CouponPolicy> couponPolicyList = couponPolicyRepository.findByDeletedStatusFalse();
    return couponPolicyList.stream()
        .map(ReadCouponPolicyResponse::fromCouponPolicy)
        .collect(Collectors.toList());
  }

  public void deleteCouponPolicy(String code, RequestUserDetails user) {
    requireMaster(user);

    CouponPolicy couponPolicy = couponPolicyRepository.findByCodeAndDeletedStatusFalse(code)
        .orElseThrow(() -> CouponPolicyException.notFound(code));
    couponPolicy.markCouponAsDeleted(couponPolicy.getName()); // 사용자명 기록
    couponPolicyRepository.save(couponPolicy);
  }

  public void updateCouponPolicy(String code, UpdateCouponPolicyRequest request,
      RequestUserDetails user) {
    requireMaster(user);

    CouponPolicy policy = findCouponPolicyOrElseThrow(code);
    couponPolicyValidator.validateForUpdatePolicy(request); // 저장소 의존 규칙이 생기면 여기서
    policy.update(request);
    couponPolicyRepository.save(policy);
  }

  public SearchCouponPolicyResponse searchCouponPolicy(String keyword, Integer page, String sortBy,
      DISCOUNT_TYPE discountType,
      RequestUserDetails user) {

    requireMaster(user);

    int pageSize = 10;
    int offset = (page - 1) * pageSize;

    Sort sort = sortBy.equalsIgnoreCase("asc") ? Sort.by(Sort.Order.asc("endAt"))
        : Sort.by(Sort.Order.desc("endAt"));
    PageRequest pageRequest = PageRequest.of(offset / pageSize, pageSize, sort);

    Page<CouponPolicySearchResult> pageResult = couponPolicyRepository.searchCouponPolicy(keyword,
        discountType, pageRequest);
    return SearchCouponPolicyResponse.fromCouponPolicyList(pageResult);
  }
}
