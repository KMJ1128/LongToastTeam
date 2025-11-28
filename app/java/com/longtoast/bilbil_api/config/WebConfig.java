package com.longtoast.bilbil_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    //  안드로이드 앱에서 JWT(Authorization 헤더)를 사용하는 요청을 허용하기 위해 CORS를 설정합니다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 🚨 수정: 모든 엔드포인트(/**)에 대해 CORS 적용

                // 🚨 핵심 수정: 모든 외부 출처(*)의 요청을 허용합니다.
                .allowedOrigins("*")

                // 🚨 핵심 수정: 모든 HTTP 메서드(GET, POST, OPTIONS, PUT, DELETE 등)를 허용합니다.
                .allowedMethods("*")

                // Authorization 헤더를 포함한 모든 헤더를 허용합니다.
                .allowedHeaders("*")

                // 자격 증명 (쿠키, HTTP 인증)은 사용하지 않음 (JWT 방식)
                .allowCredentials(false)
                .maxAge(3600);
    }
}