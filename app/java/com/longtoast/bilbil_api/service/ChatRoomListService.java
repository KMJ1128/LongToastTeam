// com.longtoast.bilbil_api.service.ChatRoomListService.java

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
     * [핵심 메서드] 사용자 ID를 기반으로 해당 사용자가 참여하는 모든 채팅방 목록을 조회합니다.
     */
    public List<ChatRoomListDTO> getMyChatRooms(Integer currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + currentUserId));

        // 1. Fetch Join을 사용하여 N+1 문제 없이 채팅방 목록 조회 (Item, Lender, Borrower 포함)
        List<ChatRoom> roomList = chatRoomRepository.findChatRoomsWithDetailsByUser(user);

        // 2. 채팅방에 연결된 모든 Item의 ID 목록을 추출
        List<Item> items = roomList.stream()
                .map(ChatRoom::getItem)
                .collect(Collectors.toList());

        // 3. Item ID 목록을 기반으로 모든 메인 이미지 정보를 한 번에 조회 (최적화)
        // Map<Long, String> : <itemId, imageUrl>
        Map<Long, String> mainImageMap = itemImageRepository.findByItemInOrderByIsMainDesc(items)
                .stream()
                // 하나의 아이템에 여러 이미지가 있더라도 isMain=true인 첫 번째 이미지만 Map에 저장
                .collect(Collectors.toMap(
                        itemImage -> itemImage.getItem().getId(),
                        ItemImage::getImageUrl,
                        (existing, replacement) -> existing // 중복 키 발생 시 기존 값 유지 (isMain=true인 값이 먼저 오도록 Query에서 처리)
                ));


        // 4. ChatRoom 리스트를 ChatRoomListDTO 리스트로 변환
        // 이 과정에서 각 방의 마지막 메시지를 조회합니다.
        return roomList.stream()
                .map(room -> convertToDto(room, currentUserId, mainImageMap))
                .collect(Collectors.toList());
    }

    /**
     * ChatRoom Entity를 ChatRoomListDTO로 변환하는 헬퍼 메서드
     */
    private ChatRoomListDTO convertToDto(ChatRoom room, Integer currentUserId, Map<Long, String> mainImageMap) {

        // --- 1. 대화 상대방 정보 결정 ---
        boolean isCurrentUserLender = room.getLender().getId().equals(currentUserId);
        User partner = isCurrentUserLender ? room.getBorrower() : room.getLender();

        // --- 2. 물품 메인 이미지 URL 조회 [쿼리 제거, Map 사용] ---
        String itemMainImageUrl = mainImageMap.get(room.getItem().getId()); // 💡 Map에서 조회

        // --- 3. 마지막 메시지 조회 ---
        Optional<ChatMessage> lastMessageOpt = chatMessageRepository
                .findTopByChatRoom_IdOrderBySentAtDesc(room.getId());

        String lastMessageContent = lastMessageOpt
                .map(msg -> {
                    if (StringUtils.hasText(msg.getContent())) {
                        return msg.getContent();
                    }
                    return StringUtils.hasText(msg.getImageUrl()) ? "[사진]" : "채팅이 시작되었습니다.";
                })
                .orElse("채팅이 시작되었습니다.");


        return ChatRoomListDTO.builder()
                // 1. 채팅방 기본 정보
                .roomId(room.getId())
                .lastMessageTime(lastMessageOpt.map(ChatMessage::getSentAt).orElse(room.getCreatedAt()))
                // 2. 상대방 정보
                .partnerId(partner.getId())
                .partnerNickname(partner.getNickname())
                .partnerProfileImageUrl(partner.getProfileImageUrl())
                // 3. 물품 정보
                .itemId(room.getItem().getId().intValue())
                .itemTitle(room.getItem().getTitle())
                .itemMainImageUrl(itemMainImageUrl)
                .itemPrice(room.getItem().getPrice())
                // 4. 마지막 메시지 내용
                .lastMessageContent(lastMessageContent)
                .build();
    }


    /**
     * ChatWebSocketController에서 채팅방 정보를 조회하기 위한 메서드 (트랜잭션 내부에서 사용)
     */
    public ChatRoom getChatRoomById(Integer roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("ChatRoom not found with ID: " + roomId));
    }
}