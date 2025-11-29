package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRoomInfoResponse {

    private Integer roomId;

    private ItemDTO item;

    private UserDTO lender;
    private UserDTO borrower;

    @Data
    @Builder
    public static class ItemDTO {
        private Long id;
        private String title;
        private Integer price;
        private String imageUrl;
    }

    @Data
    @Builder
    public static class UserDTO {
        private Integer id;
        private String nickname;
        private String profileImageUrl;
    }
}
