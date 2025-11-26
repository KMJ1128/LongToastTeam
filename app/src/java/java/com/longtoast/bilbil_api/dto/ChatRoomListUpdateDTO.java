package com.longtoast.bilbil_api.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomListUpdateDTO {
    private Integer roomId;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    // private Integer partnerId; // 필요하다면 추가
}