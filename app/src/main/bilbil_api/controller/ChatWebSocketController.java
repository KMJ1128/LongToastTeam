package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.model.ChatMessage;
import com.longtoast.bilbil_api.service.ChatRoomListService;
import com.longtoast.bilbil_api.service.ChatService;
import com.longtoast.bilbil_api.service.FcmService;
import com.longtoast.bilbil_api.repository.UserRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityNotFoundException;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatService chatService;
    private final ChatRoomListService chatRoomListService;
    private final UserRepository userRepository;
    private final FcmService fcmService;
    private final SimpMessagingTemplate template;

    @Data
    public static class ClientMessageDTO {
        private Integer senderId;
        private String content;
        private String imageUrl;
    }

    @MessageMapping("/signal/{roomId}")
    @SendTo("/topic/signal/{roomId}")
    public ChatMessage handleChatMessage(
            @DestinationVariable Integer roomId,
            @Payload ClientMessageDTO clientMsg
    ) {

        if (clientMsg.getSenderId() == null) {
            throw new IllegalArgumentException("SenderId is null");
        }

        // 1) DB에 채팅 저장
        ChatMessage saved = chatService.saveChatMessage(
                roomId,
                clientMsg.getSenderId(),
                clientMsg.getContent(),
                clientMsg.getImageUrl()
        );

        // 2) 채팅 목록 업데이트 WebSocket Push
        sendChatListUpdate(roomId, saved);

        // 3) FCM Push 전송
        sendFcmPush(roomId, saved);

        // 4) /topic/signal/{roomId}으로 메시지 브로드캐스트
        return saved;
    }

    /** 📌 채팅방 목록 갱신 WebSocket Push */
    private void sendChatListUpdate(Integer roomId, ChatMessage saved) {
        try {
            ChatRoom room = chatRoomListService.getChatRoomById(roomId);

            Integer senderId = saved.getSender().getId();
            Integer partnerId = room.getLender().getId().equals(senderId)
                    ? room.getBorrower().getId()
                    : room.getLender().getId();

            template.convertAndSendToUser(
                    senderId.toString(), "/queue/chat-list-update", saved
            );

            template.convertAndSendToUser(
                    partnerId.toString(), "/queue/chat-list-update", saved
            );

        } catch (Exception e) {
            log.error("❌ sendChatListUpdate Error", e);
        }
    }

    /** 📌 FCM Push */
    private void sendFcmPush(Integer roomId, ChatMessage saved) {

        ChatRoom room = chatRoomListService.getChatRoomById(roomId);

        Integer senderId = saved.getSender().getId();
        Integer partnerId = room.getLender().getId().equals(senderId)
                ? room.getBorrower().getId()
                : room.getLender().getId();

        User receiver = userRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("Receiver not found"));

        if (receiver.getFcmToken() == null) {
            log.warn("수신자 FCM 토큰 없음 → Push 생략");
            return;
        }

        String preview = StringUtils.hasText(saved.getContent())
                ? saved.getContent()
                : "[사진]";

        // 🔥 여기! 3개짜리 말고 4개짜리 sendMessage 호출
        fcmService.sendMessage(
                receiver.getFcmToken(),
                saved.getSender().getNickname() + "님이 보낸 메시지",
                preview,
                roomId.longValue()        // ← 이게 FcmService에서 data["roomId"]로 들어감
        );

        log.info("📨 FCM PushSent to user={}, room={}", partnerId, roomId);
    }
}