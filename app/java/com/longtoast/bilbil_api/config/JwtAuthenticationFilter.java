// com.longtoast.bilbil_api.config.JwtAuthenticationFilter.java
package com.longtoast.bilbil_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 💡 Logger 사용을 위해 추가
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Slf4j // 💡 Logger 사용
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /** HTTP 요청 헤더에서 JWT 토큰을 추출합니다. */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // ✅ [핵심 추가] 수신된 JWT 토큰을 로그로 출력 (전체 토큰은 길기 때문에 일부만 출력)
        if (token != null) {
            String shortToken = token.substring(0, Math.min(token.length(), 20)) + "...";
            log.info("🔑 [HTTP JWT RECVD] Path: {} | Token: {}", request.getRequestURI(), shortToken);
        } else {
            log.debug("⚠️ [HTTP JWT RECVD] Path: {} | No Bearer token found.", request.getRequestURI());
        }


        // 1. 토큰이 존재하고 유효성 검사를 통과했다면
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // 2. 토큰에서 Authentication 객체를 생성하고
            Authentication authentication = jwtTokenProvider.getAuthentication(token);

            // 3. SecurityContext에 인증 정보를 저장합니다.
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터로 요청을 전달합니다.
        filterChain.doFilter(request, response);
    }
}