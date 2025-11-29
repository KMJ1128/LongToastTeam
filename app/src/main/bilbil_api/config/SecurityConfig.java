// com.longtoast.bilbil_api.config.SecurityConfig.java
package com.longtoast.bilbil_api.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * ✅ 정적 리소스 등은 아예 Security 필터 체인에서 제외
     *    → JwtAuthenticationFilter 를 타지 않아서 401 방지
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                // 프로필 / 상품 이미지 등
                .requestMatchers("/uploads/**","//uploads/**")
                // 필요하다면 추가로 정적 리소스들도 여기서 제외 가능
                // .requestMatchers("/favicon.ico", "/css/**", "/js/**", "/images/**")
                ;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        http
                // WebSocket에서 CSRF가 있으면 403 발생 → 반드시 disable
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // 세션 사용 안 함 (JWT)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 🔐 JWT 필터 추가
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                /** ★ WebSocket / REST 에서 인증/인가 예외 처리 */
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                        )
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                        )
                )

                /** 🔓 인증이 필요없는 공개 엔드포인트 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/kakao/**",
                                "/naver/**",
                                "/api/chat/room/**",
                                "/api/chat/history/**",
                                "/products/**",
                                "/writeproduct/**",
                                "/ws/**",
                                "/app/**",
                                "/topic/**",
                                "/queue/**",
                                "/user/**",
                                "/stomp/**",   // /stomp/chat 엔드포인트에 대한 일반 허용
                                "/reviews/**",
                                "/search/**",
                                "/member/verification/**",
                                "/uploads/**"
                                // 여기서는 굳이 또 적을 필요 없음
                        ).permitAll()

                        // FCM 관련은 인증 필요
                        .requestMatchers("/fcm/**").authenticated()

                        // 3. 그 외 모든 요청은 인증 필요 (JWT 토큰 검사)
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
