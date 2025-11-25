// com.longtoast.bilbil_api.controller.ChatRoomController.java
package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.ChatRoomService;
import com.longtoast.bilbil_api.service.ChatService;
import com.longtoast.bilbil_api.model.ChatMessage;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatRoomController {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomController.class);

    private final ChatRoomService chatRoomService;
    private final ChatService chatService; // ChatService 주입

    // 클라이언트가 요청 본문에 담아 보낼 데이터 구조 정의 (DTO)
    @Data
    @Builder
    public static class ChatRoomCreationRequest {
        private Integer itemId;
        private Integer lenderId; // 판매자 ID
        private Integer borrowerId; // 구매자 ID
    }


    /**
     * POST /api/chat/room
     * 구매자가 특정 상품에 대해 채팅을 시작할 때 호출됩니다.
     */
    @PostMapping("/room")
    public ResponseEntity<MsgEntity> findOrCreateRoom(@RequestBody ChatRoomCreationRequest request,@AuthenticationPrincipal Integer currentUserId) {
        log.info(currentUserId.toString());
        // 🚨 [로그] 1단계: 요청 데이터 수신 확인
        log.info("API 호출 시작: POST /api/chat/room");
        log.debug("수신 요청 데이터: Item ID={}, Lender ID={}, Borrower ID={}",
                request.getItemId(), request.getLenderId(), request.getBorrowerId());


        // 1. 입력값 유효성 검증
        if (request.getItemId() == null || request.getLenderId() == null || request.getBorrowerId() == null) {
            log.warn("요청 본문 필수 필드 누락: itemId={}, lenderId={}, borrowerId={}",
                    request.getItemId(), request.getLenderId(), request.getBorrowerId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("요청 오류", "itemId, lenderId, borrowerId는 필수 입력값입니다. (JSON 형식 및 Content-Type 확인 요망)"));
        }


        try {
            // 🚨 [로그] 2단계: 서비스 호출 직전
            log.info("서비스 호출 전: findOrCreateRoom 실행 시도");

            // 2. 서비스 호출: 채팅방을 찾거나 생성합니다.
            ChatRoom room = chatRoomService.findOrCreateRoom(
                    request.getItemId(),
                    request.getLenderId(),
                    request.getBorrowerId()
            );

            // 🚨 [로그] 3단계: 서비스 호출 후 결과 확인
            Integer roomId = room.getId();
            log.info("서비스 호출 완료. ChatRoom ID={}", roomId);


            // 3. 응답 데이터(Map) 생성
            if (roomId == null) {
                log.error("심각: DB에 저장되었으나 room.getId()가 null로 반환되었습니다. ChatRoom Entity와 DB 스키마 확인 필요.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MsgEntity("오류", "채팅방 ID를 가져오지 못했습니다."));
            }

            // 🚨 [로그] 4단계: 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("roomId", roomId.toString());
            log.debug("응답 데이터 구성 완료: {}", responseData);

            log.info("채팅방 생성/조회 성공. 최종 반환 Room ID (String): {}", roomId.toString());

            // 4. MsgEntity에 Map을 담아 반환
            return ResponseEntity.ok().body(new MsgEntity("채팅방 ID 조회/생성 성공", responseData));

        } catch (EntityNotFoundException e) {
            // 🚨 [로그] 5단계: EntityNotFoundException 처리
            log.warn("요청 엔티티를 찾을 수 없음: {}", e.getMessage());
            return ResponseEntity.status(404).body(new MsgEntity("요청 오류", e.getMessage()));
        } catch (Exception e) {
            // 🚨 [로그] 6단계: 기타 예상치 못한 오류 처리
            log.error("채팅방 생성 중 예상치 못한 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MsgEntity("내부 서버 오류", "처리 중 문제가 발생했습니다: " + e.getMessage()));
        }
    }


    /**
     * GET /api/chat/history/{roomId}
     * 특정 방의 이전 메시지 기록을 불러옵니다.
     */
    @GetMapping("/history/{roomId}")
    public ResponseEntity<MsgEntity> getChatHistory(@PathVariable Integer roomId, @AuthenticationPrincipal Integer currentUserId) {

        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        try {
            // 3. 서비스 호출
            List<ChatMessage> history = chatService.getChatHistory(roomId);

            // 4. ChatMessage 리스트를 MsgEntity에 담아 반환
            return ResponseEntity.ok().body(new MsgEntity("채팅 내역 조회 성공", history));

        } catch (Exception e) {
            log.error("채팅 내역 조회 중 오류 발생 (Room ID: {})", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "내역 조회 중 문제가 발생했습니다."));
        }
    }

    /**
     * POST /api/chat/room/{roomId}/image
     * 채팅방에서 사용할 이미지를 Multipart로 업로드한 뒤 URL을 반환합니다.
     */
    @PostMapping(value = "/room/{roomId}/image", consumes = "multipart/form-data")
    public ResponseEntity<MsgEntity> uploadChatImage(
            @PathVariable Integer roomId,
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        if (image == null || image.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("요청 오류", "업로드할 이미지가 없습니다."));
        }

        try {
            String imageUrl = chatService.storeChatImage(roomId, currentUserId, image);
            Map<String, Object> data = new HashMap<>();
            data.put("imageUrl", imageUrl);
            return ResponseEntity.ok(new MsgEntity("채팅 이미지 업로드 성공", data));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("요청 오류", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("채팅 이미지 업로드 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "이미지 업로드 중 문제가 발생했습니다."));
        }
    }
}