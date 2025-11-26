package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.KakaoTokenRequest;
import com.longtoast.bilbil_api.dto.MemberTokenResponse;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.KaKaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping; // POST 매핑 사용
import org.springframework.web.bind.annotation.RequestBody; // 요청 본문 처리
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("kakao")
public class KakaoController {

    private final KaKaoService kaKaoService;

    // 🚨 인가 코드를 받는 기존의 /callback 엔드포인트는 그대로 두거나 삭제합니다.

    /** 나는 김민재입니다ddddddddss
     * [주요 엔드포인트] 안드로이드 앱에서 받은 카카오 Access Token을 처리합니다.
     * @param request 카카오 Access Token을 포함하는 요청 본문
     * @return 우리 서비스의 인증 토큰(JWT)과 회원 정보
     */
    @PostMapping("/login/token")
    public ResponseEntity<MsgEntity> loginWithKakaoToken(@RequestBody KakaoTokenRequest request) throws Exception {

        // 서비스 로직: 카카오 토큰 검증, 회원가입/로그인 처리, 서비스 토큰 발행
        MemberTokenResponse response = kaKaoService.loginWithToken(request.getAccessToken());

        return ResponseEntity.ok()
                .body(new MsgEntity("로그인 성공", response));
    }
}