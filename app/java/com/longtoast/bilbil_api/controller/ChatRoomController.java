package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.dto.ChatRoomInfoResponse;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.ChatRoomService;
import com.longtoast.bilbil_api.service.ChatService;
import com.longtoast.bilbil_api.service.ChatRoomListService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatRoomController {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomController.class);

    private final ChatRoomService chatRoomService;
    private final ChatService chatService;
    private final ChatRoomListService chatRoomListService;
    private final SimpMessagingTemplate messagingTemplate;

    @Data
    @Builder
    public static class ChatRoomCreationRequest {
        private Integer itemId;
        private Integer lenderId;   // 판매자(대여자)
        private Integer borrowerId; // 구매자(차입자)
    }

    @Data
    public static class ChatSendRequest {
        private String content;
        private String imageUrl;
    }

    /** 채팅방 생성 or 조회 */
    @PostMapping("/room")
    public ResponseEntity<MsgEntity> findOrCreateRoom(
            @RequestBody ChatRoomCreationRequest request,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        log.info("currentUserId={}", currentUserId);

        if (request.getItemId() == null || request.getLenderId() == null || request.getBorrowerId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("요청 오류", "itemId, lenderId, borrowerId는 필수 입력값입니다."));
        }

        try {
            ChatRoom room = chatRoomService.findOrCreateRoom(
                    request.getItemId(),
                    request.getLenderId(),
                    request.getBorrowerId()
            );

            Integer roomId = room.getId();
            if (roomId == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new MsgEntity("오류", "채팅방 ID를 가져오지 못했습니다."));
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("roomId", roomId.toString());

            return ResponseEntity.ok().body(new MsgEntity("채팅방 ID 조회/생성 성공", responseData));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(new MsgEntity("요청 오류", e.getMessage()));
        } catch (Exception e) {
            log.error("채팅방 생성 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "처리 중 문제가 발생했습니다: " + e.getMessage()));
        }
    }

    /** 특정 방의 채팅 내역 */
    @GetMapping("/history/{roomId}")
    public ResponseEntity<MsgEntity> getChatHistory(
            @PathVariable Integer roomId,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        try {
            List<ChatMessage> history = chatService.getChatHistory(roomId);
            return ResponseEntity.ok().body(new MsgEntity("채팅 내역 조회 성공", history));

        } catch (Exception e) {
            log.error("채팅 내역 조회 중 오류 (roomId={})", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "내역 조회 중 문제가 발생했습니다."));
        }
    }

    /** REST 기반 메시지 전송 */
    @PostMapping("/room/{roomId}/message")
    public ResponseEntity<MsgEntity> sendMessage(
            @PathVariable Integer roomId,
            @RequestBody ChatSendRequest request,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        if (!StringUtils.hasText(request.getContent()) && !StringUtils.hasText(request.getImageUrl())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MsgEntity("요청 오류", "메시지 내용이 비어 있습니다."));
        }

        try {
            ChatMessage saved = chatService.saveChatMessage(roomId, currentUserId, request.getContent(), request.getImageUrl());

            messagingTemplate.convertAndSend("/topic/signal/" + roomId, saved);
            pushChatListUpdate(roomId, saved);

            return ResponseEntity.ok(new MsgEntity("메시지 전송 성공", saved));
        } catch (Exception e) {
            log.error("채팅 메시지 저장 중 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "메시지를 전송할 수 없습니다."));
        }
    }

    @GetMapping("/room/{roomId}/info")
    public ResponseEntity<MsgEntity> getChatRoomInfo(
            @PathVariable Integer roomId,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(401)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        ChatRoomInfoResponse info = chatRoomService.getChatRoomInfo(roomId);

        return ResponseEntity.ok(new MsgEntity("채팅방 정보 조회 성공", info));
    }



    /** 채팅 이미지 업로드 */
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

    /** 📌 채팅방 목록 업데이트 Push */
    private void pushChatListUpdate(Integer roomId, ChatMessage savedMessage) {
        try {
            ChatRoom chatRoom = chatRoomListService.getChatRoomById(roomId);

            Integer senderId = savedMessage.getSender().getId();
            Integer partnerId = chatRoom.getLender().getId().equals(senderId)
                    ? chatRoom.getBorrower().getId()
                    : chatRoom.getLender().getId();

            Map<String, Object> updatePayload = new HashMap<>();
            updatePayload.put("roomId", chatRoom.getId());

            String lastMessageContent = StringUtils.hasText(savedMessage.getContent())
                    ? savedMessage.getContent()
                    : (StringUtils.hasText(savedMessage.getImageUrl()) ? "[사진]" : "");
            updatePayload.put("lastMessageContent", lastMessageContent);
            updatePayload.put("lastMessageTime", savedMessage.getSentAt());

            messagingTemplate.convertAndSendToUser(
                    partnerId.toString(),
                    "/queue/chat-list-update",
                    updatePayload
            );

            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/queue/chat-list-update",
                    updatePayload
            );

        } catch (Exception e) {
            log.error("채팅 목록 업데이트 알림 실패", e);
        }
    }


}
