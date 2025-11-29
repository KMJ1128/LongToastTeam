package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.config.JwtTokenProvider;
import com.longtoast.bilbil_api.domain.SocialLogin;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.MemberTokenResponse;
import com.longtoast.bilbil_api.dto.NaverDTO;
import com.longtoast.bilbil_api.repository.SocialLoginRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaverService {

    private final UserRepository userRepository;
    private final SocialLoginRepository socialLoginRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    // application.properties에서 네이버 사용자 정보 조회 URL 주입
    // 예: naver.user-info-url=https://openapi.naver.com/v1/nid/me
    @Value("${naver.user-info-url}")
    private String naverUserInfoUrl;

    /**
     * 네이버 Access Token을 사용하여 사용자 정보를 가져오고
     * 로그인/회원가입을 처리한 뒤, 서비스 JWT 토큰을 발급합니다.
     */
    @Transactional
    public MemberTokenResponse login(String naverAccessToken) {

        // 1. 네이버 사용자 정보 가져오기
        NaverDTO naverUser = getNaverUserInfo(naverAccessToken);
        NaverDTO.Response naverInfo = naverUser.getResponse();

        String naverId = naverInfo.getId();
        log.info("Naver User Info: {}", naverInfo.toString());

        // 2. 기존 소셜 로그인 기록 확인 (provider: "NAVER", socialId: 네이버 고유 ID)
        Optional<SocialLogin> optionalSocialLogin =
                socialLoginRepository.findByProviderAndSocialId("NAVER", naverId);

        User user;
        boolean isNewUser = !optionalSocialLogin.isPresent();

        if (isNewUser) {
            // 3-A. 신규 회원가입
            user = registerNewNaverUser(naverUser);
        } else {
            // 3-B. 기존 회원 로그인
            user = optionalSocialLogin.get().getUser();
        }

        // 4. 서비스 JWT 토큰 생성
        String serviceToken = jwtTokenProvider.createToken(user.getId());

        // 5. 클라이언트로 내려줄 응답 DTO 생성
        MemberTokenResponse tokenResponse = MemberTokenResponse.builder()
                .serviceToken(serviceToken)
                .userId(user.getId())
                .nickname(user.getNickname()) // ✅ 닉네임은 registerNewNaverUser에서 null 방어
                .address(user.getAddress())
                .phoneNumber(user.getPhoneNumber())
                .locationLatitude(user.getLocationLatitude())
                .locationLongitude(user.getLocationLongitude())
                .build();

        // 디버깅용 로그
        log.info("➡ MemberTokenResponse to client: {}", tokenResponse);

        return tokenResponse;
    }

    /**
     * 네이버 API를 호출하여 사용자 정보를 가져옵니다.
     */
    private NaverDTO getNaverUserInfo(String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        // 네이버 API 호출 시 "Bearer " 접두사 필수
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Naver API 호출 (예: https://openapi.naver.com/v1/nid/me)
            ResponseEntity<NaverDTO> response = restTemplate.exchange(
                    naverUserInfoUrl,
                    HttpMethod.GET,
                    entity,
                    NaverDTO.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new RuntimeException("네이버 사용자 정보 가져오기 실패: "
                        + (response.getBody() != null ? response.getBody().getMessage() : "응답 없음"));
            }
        } catch (HttpClientErrorException e) {
            // HTTP 4xx 에러 (토큰 만료, 잘못된 토큰 등)
            log.error("Naver API 호출 중 HTTP 에러 발생: {}", e.getMessage());
            throw new RuntimeException("네이버 인증 실패 (토큰 오류): " + e.getMessage());
        } catch (Exception e) {
            // 기타 네트워크/파싱 오류
            log.error("Naver API 호출 중 예상치 못한 에러 발생", e);
            throw new RuntimeException("네이버 사용자 정보 처리 오류");
        }
    }

    /**
     * 새로운 네이버 사용자를 서비스 DB에 등록합니다.
     * 네이버에서 받는 정보: id, name(필수), nickname(선택)
     */
    private User registerNewNaverUser(NaverDTO naverUser) {

        NaverDTO.Response res = naverUser.getResponse();

        // ✅ 닉네임 null 방어: 선택 동의라 null일 수 있으므로 name으로 대체
        String nickname = res.getNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = res.getName();
        }

        // 1. User 엔티티 생성
        User user = User.builder()
                .username(res.getName())   // username ← 네이버 "회원 이름"
                .nickname(nickname)        // nickname ← 닉네임 또는 이름
                // email, profileImageUrl 등은 현재 네이버에서 안 받으므로 생략/nullable 권장
                .password("SOCIAL_LOGIN_NAVER_HASH") // 소셜 로그인 더미 비밀번호
                .build();

        user = userRepository.save(user);

        // 2. SocialLogin 엔티티 생성 및 연결
        SocialLogin socialLogin = SocialLogin.builder()
                .user(user)
                .provider("NAVER")
                .socialId(res.getId())
                .build();

        socialLoginRepository.save(socialLogin);

        return user;
    }
}
