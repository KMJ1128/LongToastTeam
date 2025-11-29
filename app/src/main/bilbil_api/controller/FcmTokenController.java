package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.FcmTokenRequest;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FcmTokenController {

    private final UserRepository userRepository;

    @PostMapping("/fcm/token")
    public ResponseEntity<String> saveFcmToken(
            @AuthenticationPrincipal Integer userId,
            @RequestBody FcmTokenRequest request
    ) {
        System.out.println("[FCM] /fcm/token 호출됨, userId=" + userId);

        if (userId == null) {
            return ResponseEntity.status(401).body("유저 인증 정보 없음");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setFcmToken(request.getToken());
        userRepository.save(user);

        return ResponseEntity.ok("FCM 토큰 저장 완료");
    }
}