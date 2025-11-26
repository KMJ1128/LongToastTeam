// com.longtoast.bilbil_api.controller.ChatWebSocketController.java (수정됨)
package com.longtoast.bilbil_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.model.ChatMessage;
import com.longtoast.bilbil_api.service.ChatService;
import com.longtoast.bilbil_api.service.ChatRoomListService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
// import org.springframework.transaction.annotation.Transactional; // @Transactional은 Service에만 유지
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomListService chatRoomListService;

    // 💡 [수정됨] 클라이언트가 보낼 메시지 구조 정의 (DTO)
    @Data
    public static class ClientMessageDTO {
        private Integer senderId;
        private String content;
        private String imageUrl;
    }

    /**
     * 🚨 [핵심 수정] @Payload 타입을 DTO로 변경하여 DB 저장 오류 해결
     * /app/signal/{roomId}로 메시지를 수신하고, 처리 후 /topic/signal/{roomId}로 메시지를 재전송합니다.
     */
    @MessageMapping("/signal/{roomId}")
    @SendTo("/topic/signal/{roomId}")
    public ChatMessage handleChatMessage(
            @DestinationVariable Integer roomId, // 💡 Integer로 타입 변경
            @Payload ClientMessageDTO clientMessage // ✅ [핵심 수정] DTO 객체로 직접 받음
    ) throws Exception {

        if (clientMessage.getSenderId() == null) {
            log.error("Sender ID가 null입니다. 메시지 처리 실패.");
            throw new IllegalArgumentException("Sender ID is required.");
        }

        String content = clientMessage.getContent() != null ? clientMessage.getContent() : "";
        String imageUrl = clientMessage.getImageUrl();

        if (!StringUtils.hasText(content) && !StringUtils.hasText(imageUrl)) {
            log.warn("메시지 내용과 이미지가 모두 비어있습니다. 전송 취소.");
            return null;
        }

        ChatMessage savedMessage = null;

        try {
            // 2. 서비스 호출: DB 저장 (ChatService의 @Transactional이 커밋 보장)
            savedMessage = chatService.saveChatMessage(
                    roomId,
                    clientMessage.getSenderId(),
                    content,
                    imageUrl
            );

            log.info("✅ [WS MSG] DB 저장 성공. Room ID: {}, Sender: {}", roomId, clientMessage.getSenderId());

            // 3. 채팅방 목록 업데이트 알림 푸시
            pushChatListUpdateNotification(roomId, savedMessage);

        } catch (EntityNotFoundException e) {
            log.error("❌ [WS MSG] 채팅방 또는 사용자 엔티티를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ [WS MSG] 메시지 처리 중 알 수 없는 오류 발생", e);
            throw e;
        }

        // 4. WebSocket 채널에 메시지 재전송 (/topic/signal/{roomId})
        return savedMessage;
    }

    /**
     * 메시지를 전송한 후, 채팅방 목록을 갱신하도록 양쪽 사용자에게 알림을 보냅니다.
     */
    private void pushChatListUpdateNotification(Integer roomId, ChatMessage savedMessage) {
        try {
            // 1. ChatRoom 정보 조회
            ChatRoom chatRoom = chatRoomListService.getChatRoomById(roomId);

            // 2. 대화 당사자 식별
            Integer senderId = savedMessage.getSender().getId();
            Integer partnerId = chatRoom.getLender().getId().equals(senderId) ?
                    chatRoom.getBorrower().getId() : chatRoom.getLender().getId();

            // 3. 알림 페이로드 생성
            Map<String, Object> updatePayload = new HashMap<>();
            updatePayload.put("roomId", chatRoom.getId());
            String lastMessageContent = StringUtils.hasText(savedMessage.getContent())
                    ? savedMessage.getContent()
                    : (StringUtils.hasText(savedMessage.getImageUrl()) ? "[사진]" : "");
            updatePayload.put("lastMessageContent", lastMessageContent);
            updatePayload.put("lastMessageTime", savedMessage.getSentAt());

            // 4. 발신자(Sender)와 수신자(Partner) 모두에게 알림 전송 (String ID 사용)

            // 4-1. 수신자에게 푸시
            messagingTemplate.convertAndSendToUser(
                    partnerId.toString(),
                    "/queue/chat-list-update",
                    updatePayload
            );

            // 4-2. 발신자에게 푸시 (자신이 보낸 메시지도 목록을 갱신해야 하므로)
            messagingTemplate.convertAndSendToUser(
                    senderId.toString(),
                    "/queue/chat-list-update",
                    updatePayload
            );

            log.info("채팅방 목록 업데이트 알림 푸시 완료: Room ID {} -> 수신자: {}, 발신자: {}",
                    roomId, partnerId, senderId);

        } catch (EntityNotFoundException e) {
            log.error("❌ [WS PUSH] 채팅방 정보를 찾을 수 없어 목록 업데이트 알림 실패: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [WS PUSH] 채팅방 목록 업데이트 푸시 중 예상치 못한 오류 발생", e);
        }
    }
}