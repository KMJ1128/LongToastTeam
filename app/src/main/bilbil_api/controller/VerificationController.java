package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.VerificationRequest;
import com.longtoast.bilbil_api.dto.VerificationResponse;
import com.longtoast.bilbil_api.exception.PhoneAlreadyUsedException;
import com.longtoast.bilbil_api.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/member/verification")
public class VerificationController {

    private final VerificationService verificationService;

    /**
     * 1단계: 전화번호 인증 요청 (인증 코드 생성 및 SMS URL 반환)
     * POST /member/verification/request
     */
    @PostMapping("/request")
    public ResponseEntity<MsgEntity> requestVerification(
            @AuthenticationPrincipal Integer currentUserId,
            @RequestBody VerificationRequest request
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        try {
            VerificationResponse response =
                    verificationService.requestVerification(currentUserId, request.getPhoneNumber());

            return ResponseEntity.ok()
                    .body(new MsgEntity("인증 요청 성공 및 SMS URL 반환", response));

        } catch (PhoneAlreadyUsedException e) {
            // 🔥 핵심: 전화번호가 이미 다른 계정에 등록됨
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("이미 다른 소셜로그인으로 가입된 사용자입니다", null));

        } catch (Exception e) {
            // 나머지 예외 처리
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("서버 오류", "인증 요청을 처리할 수 없습니다."));
        }
    }

    /**
     * 2단계: 인증 확인 (클라이언트가 문자를 보낸 후, 서버가 메일함 확인)
     * POST /member/verification/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<MsgEntity> confirmVerification(
            @AuthenticationPrincipal Integer currentUserId,
            @RequestBody VerificationRequest request
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        // 인증 로직 실행
        verificationService.confirmVerification(currentUserId, request.getPhoneNumber());

        return ResponseEntity.ok()
                .body(new MsgEntity("전화번호 인증 성공", null));
    }
}