package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.ItemImage;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.ChatRoomListDTO;
import com.longtoast.bilbil_api.model.ChatMessage;
import com.longtoast.bilbil_api.repository.ChatMessageRepository;
import com.longtoast.bilbil_api.repository.ChatRoomRepository;
import com.longtoast.bilbil_api.repository.ItemImageRepository;
import com.longtoast.bilbil_api.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomListService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ItemImageRepository itemImageRepository;

    /**
     * 특정 사용자(currentUserId)가 참여 중인 모든 채팅방 목록 조회
     */
    public List<ChatRoomListDTO> getMyChatRooms(Integer currentUserId) {

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + currentUserId));

        // 1. Fetch Join으로 채팅방 + 아이템 + 유저 정보 한번에 조회
        List<ChatRoom> roomList = chatRoomRepository.findChatRoomsWithDetailsByUser(user);

        // 2. 모든 아이템 리스트 추출
        List<Item> items = roomList.stream()
                .map(ChatRoom::getItem)
                .collect(Collectors.toList());

        // 3. 모든 아이템에 대한 대표 이미지 조회
        Map<Long, String> mainImageMap = itemImageRepository.findByItemInOrderByIsMainDesc(items)
                .stream()
                .collect(Collectors.toMap(
                        itemImage -> itemImage.getItem().getId(),
                        ItemImage::getImageUrl,
                        (existing, replacement) -> existing
                ));

        // 4. DTO 변환
        return roomList.stream()
                .map(room -> convertToDto(room, currentUserId, mainImageMap))
                .collect(Collectors.toList());
    }

    /**
     * ChatRoom → ChatRoomListDTO 변환
     */
    private ChatRoomListDTO convertToDto(ChatRoom room, Integer currentUserId, Map<Long, String> mainImageMap) {

        boolean isCurrentUserLender = room.getLender().getId().equals(currentUserId);
        User partner = isCurrentUserLender ? room.getBorrower() : room.getLender();

        // 대표 이미지
        String itemMainImageUrl = mainImageMap.get(room.getItem().getId());

        // 마지막 메시지
        Optional<ChatMessage> lastMessageOpt =
                chatMessageRepository.findTopByChatRoom_IdOrderBySentAtDesc(room.getId());

        String lastMessageContent = lastMessageOpt
                .map(msg -> {
                    if (StringUtils.hasText(msg.getContent())) {
                        return msg.getContent();
                    }
                    return StringUtils.hasText(msg.getImageUrl()) ? "[사진]" : "채팅이 시작되었습니다.";
                })
                .orElse("채팅이 시작되었습니다.");

        // ⭐ unreadCount 계산 (상대방이 보낸 읽지 않은 메시지 수)
        int unreadCount = chatMessageRepository.countUnreadMessages(room.getId(), currentUserId);

        return ChatRoomListDTO.builder()
                .roomId(room.getId())
                .lastMessageTime(lastMessageOpt.map(ChatMessage::getSentAt).orElse(room.getCreatedAt()))

                .partnerId(partner.getId())
                .partnerNickname(partner.getNickname())
                .partnerProfileImageUrl(partner.getProfileImageUrl())

                .itemId(room.getItem().getId().intValue())
                .itemTitle(room.getItem().getTitle())
                .itemMainImageUrl(itemMainImageUrl)
                .itemPrice(room.getItem().getPrice())

                .lastMessageContent(lastMessageContent)

                // ⭐ 추가된 unreadCount
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * WebSocket 및 Controller에서 채팅방 조회할 때 사용
     */
    public ChatRoom getChatRoomById(Integer roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found with ID: " + roomId));
    }
}
