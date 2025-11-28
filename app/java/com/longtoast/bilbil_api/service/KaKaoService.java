// com.longtoast.bilbil_api.service.KaKaoService.java
package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.SocialLogin;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.KakaoDTO;
import com.longtoast.bilbil_api.dto.MemberTokenResponse;
import com.longtoast.bilbil_api.repository.SocialLoginRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.longtoast.bilbil_api.config.JwtTokenProvider;


@Service
@RequiredArgsConstructor
@Transactional // DB 변경이 포함되므로 트랜잭션 관리
public class KaKaoService {

    private static final Logger log = LoggerFactory.getLogger(KaKaoService.class);

    private final WebClient.Builder webClientBuilder;
    private final UserRepository userRepository;
    private final SocialLoginRepository socialLoginRepository;
    private final JwtTokenProvider jwtTokenProvider;


    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    /**
     * [주력 로그인 로직] 안드로이드 앱에서 전달받은 카카오 Access Token을 처리합니다.
     */
    public MemberTokenResponse loginWithToken(String kakaoAccessToken) throws Exception {

        KakaoDTO kakaoInfo = getUserInfoFromKakao(kakaoAccessToken);

        log.info("--- 카카오 로그인 사용자 정보 ---");
        log.info("카카오 로그인 사용자 정보: ID={}, 닉네임={}", kakaoInfo.getId(), kakaoInfo.getNickname());
        log.info("-----------------------------");

        String socialId = "kakao_" + kakaoInfo.getId();

        SocialLogin socialLogin = socialLoginRepository
                .findBySocialId(socialId)
                .orElse(null);

        User user;

        if (socialLogin != null) {
            user = socialLogin.getUser(); // 이미 존재하면 해당 User 가져오기
        } else {
            // ✅ [핵심 수정] 신규 회원 생성: nickname 필드를 Builder에서 제거하여 DB에 NULL로 저장되도록 합니다.
            String baseNickname = kakaoInfo.getNickname();

            user = User.builder()
                    .username(baseNickname) // 💡 카카오 닉네임은 username에 저장
                    // nickname 필드를 설정하지 않음 -> DB의 nullable=true 제약 조건에 따라 NULL 저장
                    .build();
            user = userRepository.save(user);

            // SocialLogin 기록 생성
            socialLoginRepository.save(SocialLogin.builder()
                    .user(user)
                    .provider("kakao")
                    .socialId(socialId)
                    .accessToken(kakaoAccessToken)
                    .build());
        }

        // 3. 우리 서비스 인증 토큰 발행
        String serviceToken = jwtTokenProvider.createToken(user.getId());

        // 4. 결과 반환
        return new MemberTokenResponse(
                serviceToken,
                user.getId(),
                user.getNickname(), // DB에서 NULL이면 NULL이 반환됨
                user.getAddress(),
                user.getLocationLatitude(),
                user.getLocationLongitude(),
                user.getCreditScore(),
                user.getProfileImageUrl()
        );
    }

    /**
     * [내부 Helper 함수] 카카오 Access Token으로 사용자 정보를 조회
     */
    @Transactional(readOnly = true) // DB 조작 없음
    private KakaoDTO getUserInfoFromKakao(String kakaoAccessToken) throws Exception {

        System.out.println("DEBUG: WebClient로 카카오 토큰 검증 및 사용자 정보 조회 중...");

        KakaoDTO kakaoInfo = webClientBuilder.build()
                .get()
                .uri("https://kapi.kakao.com/v2/user/me")
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> Mono.error(new RuntimeException("Kakao API Error: " + response.statusCode()))
                )
                .bodyToMono(KakaoDTO.class)
                .block();

        if (kakaoInfo == null || kakaoInfo.getId() == 0) {
            throw new Exception("Failed to retrieve user info from Kakao or ID is zero.");
        }

        return kakaoInfo;
    }
}