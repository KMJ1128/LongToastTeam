package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.model.ChatMessage;
import com.longtoast.bilbil_api.repository.ChatMessageRepository;
import com.longtoast.bilbil_api.repository.ChatRoomRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile; // 👈 MultipartFile 추가
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import java.util.Base64; // 👈 Base64 관련 import 제거

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    /**
     * 새로운 채팅 메시지를 DB에 저장합니다. (DB 저장만 담당)
     * ✅ @Transactional이 이 메서드의 DB 커밋을 보장합니다.
     */
    @Transactional
    public ChatMessage saveChatMessage(Integer roomId, Integer senderId, String content, String imageUrl) {

        // 1. 엔티티 관계 객체 조회
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found with ID: " + roomId));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + senderId));



        // 2. ChatMessage 객체 생성 및 엔티티 관계 설정
        ChatMessage message = new ChatMessage();
        message.setChatRoom(chatRoom);
        message.setSender(sender);

        message.setContent(content);
        // 🌟 Base64 로직 제거, MultipartFile 처리 후 받은 imageUrl을 직접 사용
        message.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl : null);
        message.setSentAt(LocalDateTime.now());
        message.setIsRead(false);

        // DB 저장 후 바로 반환 (푸시 로직은 Controller로 이관)
        return chatMessageRepository.save(message);
    }

    /**
     * 채팅 이미지를 서버에 저장하고, 저장된 URL을 반환합니다.
     * 이 메서드는 Controller 레이어에서 호출되어야 하며, 반환된 URL을
     * saveChatMessage 메서드의 imageUrl 인수로 전달해야 합니다.
     */
    public String storeChatImage(Integer roomId, Integer senderId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지가 없습니다.");
        }

        // 1. 엔티티 존재 확인 (채팅방 경로 생성을 위해 ChatRoom 객체 필요)
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found with ID: " + roomId));

        userRepository.findById(senderId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + senderId));

        try {
            // 2. 저장 경로 설정 및 디렉토리 생성
            // 예: /uploads/chat/123 (roomId)
            Path uploadDir = Paths.get("/uploads/chat/" + chatRoom.getId());
            Files.createDirectories(uploadDir);

            // 3. 파일 이름 생성 (중복 방지: senderId_현재시간.jpg)
            String filename = String.format("chat_%d_%d.jpg", senderId, System.currentTimeMillis());
            Path filePath = uploadDir.resolve(filename);

            // 4. 파일 저장
            image.transferTo(filePath);

            // 5. 클라이언트 접근 가능 URL 반환
            return String.format("/uploads/chat/%d/%s", chatRoom.getId(), filename);
        } catch (IOException e) {
            throw new RuntimeException("채팅 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }


    /**
     * 특정 방의 이전 메시지 기록을 불러오는 메서드 (채팅방 진입 시 사용)
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getChatHistory(Integer roomId) {
        // 1. 방이 존재하는지 확인
        if (!chatRoomRepository.existsById(roomId)) {
            throw new EntityNotFoundException("ChatRoom not found with ID: " + roomId);
        }

        // 2. 해당 방의 모든 메시지를 시간 순으로 조회
        return chatMessageRepository.findByChatRoom_IdOrderBySentAtAsc(roomId);
    }



    public void broadcastMessage(Integer roomId, ChatMessage message) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("roomId", roomId);
        payload.put("senderId", message.getSender().getId());
        payload.put("content", message.getContent());
        payload.put("imageUrl", message.getImageUrl()); // null 가능 OK
        payload.put("sentAt", message.getSentAt().toString());

        messagingTemplate.convertAndSend(
                "/sub/chat/room/" + roomId,
                payload
        );
    }

}