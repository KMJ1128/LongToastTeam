// com.longtoast.bilbil_api.config.AuthChannelInterceptor.java
package com.longtoast.bilbil_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component // ✅ Spring Bean으로 등록
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            log.info("📢 [WS AUTH] STOMP CONNECT 명령 수신. 인증 시도...");

            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null) {
                // ANDROID 클라이언트가 "Authorization:Bearer <token>" 형식으로 전송하는 경우를 허용하기 위해
                // 접두사 공백 유무와 대소문자를 모두 무시하고 토큰을 추출한다.
                String normalized = authHeader.trim();
                if (normalized.toLowerCase().startsWith("bearer")) {
                    String token = normalized.substring("bearer".length()).trim();
                    log.info("🔎 [WS AUTH] Authorization 헤더 감지. 토큰 검증 시작.");

                    if (jwtTokenProvider.validateToken(token)) {
                        Authentication authentication = jwtTokenProvider.getAuthentication(token);

                        accessor.setUser(authentication);
                        log.info("✅ [WS AUTH] STOMP 세션 인증 성공! 사용자 ID: {}", authentication.getName());
                    } else {
                        log.warn("❌ [WS AUTH] 토큰 유효성 검증 실패. 유효하지 않은 JWT 토큰입니다.");
                    }
                } else {
                    log.warn("⚠️ [WS AUTH] Authorization 헤더에 Bearer 접두사가 없습니다: {}", authHeader);
                }
            } else {
                log.warn("⚠️ [WS AUTH] Authorization 헤더(Bearer 토큰)가 STOMP CONNECT 프레임에 없습니다.");
            }
        }

        return message;
    }
}