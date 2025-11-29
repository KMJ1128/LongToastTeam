package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomListDTO {
    // 1. 채팅방 기본 정보
    private Integer roomId;
    private LocalDateTime lastMessageTime; // 마지막 메시지 시간

    // 2. 상대방 정보 (대화 상대)
    private Integer partnerId;
    private String partnerNickname;
    private String partnerProfileImageUrl;

    // 3. 물품 정보
    private Integer itemId;
    private String itemTitle;
    private String itemMainImageUrl; // 물품의 대표 이미지 URL
    private int itemPrice;

    // 4. 마지막 메시지 내용 (선택 사항)
    private String lastMessageContent;
}