package com.longtoast.bilbil_api.config;

import com.longtoast.bilbil_api.service.NaverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 네이버 로그인 관련 설정을 위한 Configuration 클래스입니다.
 * - RestTemplate Bean 등록 (Naver API 호출용)
 * - NaverService에 @Value로 주입될 설정값이 필요합니다.
 */
@Configuration
public class NaverConfig {

    /**
     * NaverService에서 외부 API (네이버 사용자 정보 조회)를 호출하기 위해 필요한
     * RestTemplate Bean을 등록합니다.
     * * @return RestTemplate 인스턴스
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}