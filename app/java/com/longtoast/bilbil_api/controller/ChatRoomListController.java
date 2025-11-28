package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.ChatRoomListDTO;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.ChatRoomListService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat") // 예시: /chat 엔드포인트 사용
public class ChatRoomListController {

    private final ChatRoomListService chatRoomListService;

    /**
     * [채팅방 목록 조회] 현재 로그인된 사용자가 참여하는 모든 채팅방 목록을 조회합니다.
     * GET /chat/rooms
     */
    @GetMapping("/rooms")
    public ResponseEntity<MsgEntity> getMyChatRoom(
            @AuthenticationPrincipal Integer currentUserId // 🚨 JWT에서 추출한 ID를 받음
    ) {
        // 1. [보안 필수] 인증된 ID가 없으면 접근 거부
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다. 로그인이 필요합니다.");
        }

        try {
            // 2. 서비스 호출: 내 채팅방 목록 조회
            List<ChatRoomListDTO> roomLists = chatRoomListService.getMyChatRooms(currentUserId);

            // 3. 성공 응답 생성
            return ResponseEntity.ok()
                    .body(new MsgEntity("채팅방 목록 조회 성공", roomLists));

        } catch (EntityNotFoundException e) {
            // 사용자를 찾지 못한 경우 (Service에서 발생 가능)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("오류", e.getMessage()));
        }
    }
}