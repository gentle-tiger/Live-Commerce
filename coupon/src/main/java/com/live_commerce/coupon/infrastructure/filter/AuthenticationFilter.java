package com.live_commerce.coupon.infrastructure.filter;

import com.live_commerce.coupon.infrastructure.security.RequestUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets; // [ADDED]
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticationFilter extends OncePerRequestFilter {

	// 필터를 아예 적용하지 않을 경로들
	private final RequestMatcher skipMatcher = new OrRequestMatcher(
			new AntPathRequestMatcher("/actuator/**"),
			new AntPathRequestMatcher("/swagger-ui/**"),
			new AntPathRequestMatcher("/v3/api-docs/**"),
			new AntPathRequestMatcher("/api/v1/auth/**"),
			new AntPathRequestMatcher("/api/v1/issued-coupons/*/signup-first"),
			new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name())
	);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return skipMatcher.matches(request); // true면 doFilterInternal 자체가 실행되지 않음
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		// 요청 헤더에서 사용자 정보 추출
		String userIdHeader = request.getHeader("X-User-Id");
		String username     = request.getHeader("X-User-Username");
		String role         = request.getHeader("X-User-Role");

		if (userIdHeader == null || username == null || role == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
			return;
		}

		UUID userId;
		try {
			userId = UUID.fromString(userIdHeader);
		} catch (IllegalArgumentException e) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		if (!role.startsWith("ROLE_")) {
			role = "ROLE_" + role;
		}

		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
		UserDetails userDetails = new RequestUserDetails(userId, username, authorities);

		// 인증 정보 설정
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userDetails, null, authorities)
		);

		// 필터 체인으로 넘김
		chain.doFilter(request, response);
	}
}