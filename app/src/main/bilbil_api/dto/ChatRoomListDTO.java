package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomListDTO {

    private Integer roomId;
    private LocalDateTime lastMessageTime;

    private Integer partnerId;
    private String partnerNickname;
    private String partnerProfileImageUrl;

    private Integer itemId;
    private String itemTitle;
    private String itemMainImageUrl;
    private int itemPrice;

    private String lastMessageContent;

    // ⭐ 추가
    private int unreadCount;
}
