package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.NaverTokenRequest;
import com.longtoast.bilbil_api.dto.MemberTokenResponse;
import com.longtoast.bilbil_api.service.NaverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/naver")
@RequiredArgsConstructor
@Slf4j
public class NaverController {

    private final NaverService naverService;

    /**
     * 안드로이드 클라이언트로부터 받은 네이버 Access Token을 처리합니다.
     */
    @PostMapping("/login/token")
    public ResponseEntity<MsgEntity> loginWithToken(@RequestBody NaverTokenRequest request) {

        log.info("Naver login requested with Access Token: {}", request.getAccessToken());

        // NaverService를 호출하여 인증 및 로그인 처리
        MemberTokenResponse response = naverService.login(request.getAccessToken());

        return ResponseEntity.ok(MsgEntity.builder()
                .message("네이버 인증 및 로그인 성공")
                .data(response)
                .build());
    }
}