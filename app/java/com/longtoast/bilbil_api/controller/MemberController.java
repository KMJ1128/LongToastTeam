package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MemberDTO;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("member")
public class MemberController {

    private final MemberService memberService;

    /**
     * [최종] 현재 로그인된 사용자의 정보를 JWT 토큰에서 추출한 ID로 조회하는 API
     */
    @GetMapping("/info")
    public ResponseEntity<MsgEntity> getMyInfo(
            @AuthenticationPrincipal Integer currentUserId
    ) throws Exception {

        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다. 로그인이 필요합니다.");
        }

        MemberDTO myInfo = memberService.getMemberInfoFromDb(currentUserId);

        return ResponseEntity.ok()
                .body(new MsgEntity("내 정보 조회 성공", myInfo));
    }

    /**
     * ✅ [핵심 추가] 프로필 정보 업데이트 (회원가입 완료 및 정보 수정 시 사용)
     * PUT /member/profile
     * @param currentUserId JWT에서 추출된 ID (인증된 사용자)
     * @param memberDTO 업데이트할 닉네임, 주소, 위치 정보 포함
     */
    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MsgEntity> updateMemberProfile(
            @AuthenticationPrincipal Integer currentUserId,
            @RequestPart("member") MemberDTO memberDTO,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        // 💡 현재 인증된 사용자가 자신의 프로필만 수정하도록 ID를 강제합니다.
        // DTO에 포함된 ID 대신 JWT에서 추출한 ID를 사용합니다.
        memberService.updateMemberProfile(currentUserId, memberDTO, profileImage);

        return ResponseEntity.ok().body(new MsgEntity("프로필 정보 업데이트 및 회원가입 완료", null));
    }


    /**
     * 💡 특정 사용자 프로필 상세 정보 조회 엔드포인트
     */
    @GetMapping("/{userId}")
    public ResponseEntity<MsgEntity> getMemberProfile(@PathVariable Integer userId) {
        MemberDTO profile = memberService.getMemberInfoFromDb(userId);
        return ResponseEntity.ok().body(new MsgEntity("사용자 프로필 조회 성공", profile));
    }
}
