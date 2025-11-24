// com.longtoast.bilbil.dto/ChatRoomListDTO.kt (안드로이드)

package com.longtoast.bilbil.dto

data class ChatRoomListDTO(
    // 1. 채팅방 기본 정보
    val roomId: Int?,               // 백엔드 Integer roomId
    val lastMessageTime: String?,   // 백엔드 LocalDateTime (String으로 받음)

    // 2. 상대방 정보
    val partnerId: Int?,
    val partnerNickname: String?,
    val partnerProfileImageUrl: String?,

    // 3. 물품 정보
    val itemId: Int?,
    val itemTitle: String?,
    val itemMainImageUrl: String?, // 💡 [유지] 물품의 대표 이미지 URL (목록 썸네일)
    val itemPrice: Int?,

    // 4. 마지막 메시지 내용
    val lastMessageContent: String?,

    // 5. 읽지 않은 메시지 수
    val unreadCount: Int? = 0,
)