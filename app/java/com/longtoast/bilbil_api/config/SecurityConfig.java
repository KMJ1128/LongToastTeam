// com.longtoast.bilbil_api.config.SecurityConfig.java
package com.longtoast.bilbil_api.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        http
                // WebSocket에서 CSRF가 있으면 403 발생 → 반드시 disable
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                /** ★ 핵심: WebSocket 예외 처리 정상화 */
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                        )
                        .accessDeniedHandler((req, res, e) ->
                                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                        )
                )

                /** WebSocket 핸드셰이크가 Security에 막히지 않도록 전체 허용 */
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
                                "/stomp/**", // /stomp/chat 엔드포인트에 대한 일반적인 허용
                                "/reviews/**",
                                "/search/**",
                                "/uploads/**"

                        ).permitAll()
                        .requestMatchers("/fcm/**").authenticated()
                        // 3. 그 외 모든 요청은 인증 필요 (JWT 토큰 검사)
                        .anyRequest().authenticated()
                );


        return http.build();
    }
}
