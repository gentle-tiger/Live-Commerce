package com.live_commerce.coupon.presentation.controller;

import com.live_commerce.coupon.application.service.IssuedCouponService;
import com.live_commerce.coupon.domain.exception.IssuedCouponException;
import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import com.live_commerce.coupon.presentation.dto.response.UsedIssuedCouponResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IssuedCouponControllerV2 테스트
 *
 * 📚 컨트롤러 테스트 학습 포인트:
 * 1. @WebMvcTest: 컨트롤러 레이어만 테스트 (Service는 Mock 사용)
 * 2. MockMvc: HTTP 요청/응답 시뮬레이션
 * 3. @MockBean: Service를 가짜 객체로 대체
 * 4. given-when-then: Mockito BDD 스타일
 * 5. @WithMockUser: 인증된 사용자 시뮬레이션
 */
@WebMvcTest(IssuedCouponControllerV2.class)
class IssuedCouponControllerV2Test {

    @Autowired
    private MockMvc mockMvc;  // HTTP 요청을 시뮬레이션하는 도구

    @MockBean
    private IssuedCouponService issuedCouponService;  // 실제 서비스 대신 가짜 객체 사용

    /**
     * 👨‍🏫 선생님 예시 1: 쿠폰 사용 성공 테스트
     *
     * 📖 배울 점:
     * - MockMvc로 HTTP PATCH 요청 보내기
     * - @MockBean으로 서비스 동작 정의 (given)
     * - JSON 응답 검증 (jsonPath)
     * - HTTP 상태 코드 검증 (status())
     */
    @Test
    @DisplayName("쿠폰 사용 성공 - 200 OK 응답")
    @WithMockUser(username = "testuser")  // 인증된 사용자로 테스트
    void useCoupon_Success_Returns200() throws Exception {
        // given: Service가 성공적으로 쿠폰을 사용했다고 가정
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UsedIssuedCouponResponse mockResponse = UsedIssuedCouponResponse.builder()
                .issuedCouponId(couponId)
                .couponCode("WINTER_SALE")
                .userId(userId)
                .isUsed(true)
                .usedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        // Service의 useCouponAndPublishEvent 메서드가 호출되면 mockResponse 반환
        given(issuedCouponService.useCouponAndPublishEvent(eq(couponId), any(RequestUserDetails.class)))
                .willReturn(mockResponse);

        // when & then: PATCH 요청을 보내고 응답 검증
        mockMvc.perform(patch("/api/v2/issued-coupons/{couponId}/use", couponId)
                        .with(user(new RequestUserDetails(userId, "testuser", null))))
                .andExpect(status().isOk())  // 200 OK
                .andExpect(jsonPath("$.status").value("success"))  // JSON 응답의 status 필드
                .andExpect(jsonPath("$.data.issuedCouponId").value(couponId.toString()))
                .andExpect(jsonPath("$.data.couponCode").value("WINTER_SALE"))
                .andExpect(jsonPath("$.data.isUsed").value(true));

        // Service 메서드가 실제로 호출되었는지 검증
        verify(issuedCouponService).useCouponAndPublishEvent(eq(couponId), any(RequestUserDetails.class));
    }

    /**
     * 👨‍🏫 선생님 예시 2: 존재하지 않는 쿠폰 - 예외 처리
     *
     * 📖 배울 점:
     * - Service에서 예외가 발생했을 때 처리
     * - given().willThrow()로 예외 상황 시뮬레이션
     * - 예외 발생 시 적절한 HTTP 상태 코드 반환 확인
     */
    @Test
    @DisplayName("존재하지 않는 쿠폰 사용 시도 - 404 Not Found")
    @WithMockUser(username = "testuser")
    void useCoupon_NotFound_Returns404() throws Exception {
        // given: Service가 IssuedCouponException을 던진다고 가정
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        given(issuedCouponService.useCouponAndPublishEvent(eq(couponId), any(RequestUserDetails.class)))
                .willThrow(IssuedCouponException.notFound(couponId, userId));

        // when & then: 404 응답 확인
        mockMvc.perform(patch("/api/v2/issued-coupons/{couponId}/use", couponId)
                        .with(user(new RequestUserDetails(userId, "testuser", null))))
                .andExpect(status().isNotFound());  // 404 Not Found

        verify(issuedCouponService).useCouponAndPublishEvent(eq(couponId), any(RequestUserDetails.class));
    }

    /**
     * 🎓 학생 과제 1: 이미 사용한 쿠폰 테스트
     *
     * 📝 TODO: 아래 테스트를 완성하세요!
     *
     * 테스트 시나리오:
     * - 이미 사용한 쿠폰을 다시 사용하려 할 때
     * - Service가 IssuedCouponException.alreadyUsed() 예외를 던짐
     * - 409 Conflict 응답 반환
     *
     * 힌트:
     * 1. given(): issuedCouponService.useCouponAndPublishEvent()가 IssuedCouponException.alreadyUsed() 던지도록 설정
     * 2. mockMvc.perform(): PATCH 요청
     * 3. andExpect(): status().isConflict() 검증 (409)
     */
    @Test
    @DisplayName("이미 사용한 쿠폰 재사용 시도 - 409 Conflict")
    @WithMockUser(username = "testuser")
    void useCoupon_AlreadyUsed_Returns409() throws Exception {
        // given: 이미 사용한 쿠폰
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // TODO: Service가 alreadyUsed 예외를 던지도록 설정
        // given(issuedCouponService.useCouponAndPublishEvent(...))
        //     .willThrow(IssuedCouponException.alreadyUsed(couponId));

        // when & then: 409 Conflict 응답
        // TODO: mockMvc.perform()으로 PATCH 요청
        // TODO: andExpect(status().isConflict()) 검증

        // TODO: verify()로 메서드 호출 확인
    }

    /**
     * 🎓 학생 과제 2: 만료된 쿠폰 사용 시도
     *
     * 📝 TODO: 테스트를 완성하세요!
     *
     * 테스트 시나리오:
     * - 만료된 쿠폰을 사용하려 할 때
     * - Service가 IssuedCouponException (만료 관련) 던짐
     * - 적절한 HTTP 상태 코드 반환
     *
     * 힌트:
     * - IssuedCouponException의 어떤 메서드를 사용해야 할지 생각해보세요
     * - 만료된 쿠폰은 어떤 상태 코드를 반환해야 할까요? (400? 409? 422?)
     */
    @Test
    @DisplayName("만료된 쿠폰 사용 시도 - 적절한 에러 응답")
    @WithMockUser(username = "testuser")
    void useCoupon_Expired_ReturnsError() throws Exception {
        // given: 만료된 쿠폰
        UUID couponId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // TODO: Service가 예외를 던지도록 설정
        // IssuedCouponException의 어떤 정적 메서드를 사용해야 할까요?

        // when & then: 에러 응답 검증
        // TODO: mockMvc.perform()으로 PATCH 요청
        // TODO: 적절한 상태 코드 검증

        // TODO: verify()로 메서드 호출 확인
    }

    /**
     * 🎓 학생 과제 3: 첫 가입 쿠폰 발급 성공
     *
     * 📝 TODO: 테스트를 완성하세요!
     *
     * 테스트 시나리오:
     * - POST /api/v2/issued-coupons/{userId}/signup-first
     * - 첫 가입 쿠폰 발급 성공
     * - 204 No Content 응답 (ResponseUtil.noContent())
     *
     * 힌트:
     * 1. Service의 issueFirstCouponDirectly() 메서드 모킹 필요 (void 메서드)
     * 2. MockMvc로 POST 요청 보내기 (post() 사용)
     * 3. status().isNoContent() 검증 (204)
     */
    @Test
    @DisplayName("첫 가입 쿠폰 발급 성공 - 204 No Content")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void signupFirstCoupon_Success_Returns204() throws Exception {
        // given: 새로운 사용자 ID
        UUID userId = UUID.randomUUID();

        // TODO: Service의 issueFirstCouponDirectly() 메서드 모킹
        // void 메서드이므로 willDoNothing() 사용
        // given(issuedCouponService).willDoNothing()...

        // when & then: POST 요청 후 204 응답 검증
        // TODO: mockMvc.perform(post(...))
        // TODO: andExpect(status().isNoContent())

        // TODO: verify()로 메서드 호출 확인
    }

    /**
     * 💡 추가 과제 (선택): 인증 없이 요청 시 401/403
     *
     * 📝 도전 과제: @WithMockUser 없이 요청하면?
     *
     * 힌트:
     * - @WithMockUser 어노테이션을 제거하고 테스트
     * - status().isUnauthorized() 또는 status().isForbidden() 검증
     */
    @Test
    @DisplayName("인증 없이 쿠폰 사용 시도 - 401 Unauthorized")
    void useCoupon_WithoutAuth_Returns401() throws Exception {
        // TODO: 인증 없이 요청 시나리오 작성 (도전 과제)
        // 힌트: @WithMockUser를 사용하지 않고 요청
    }
}
