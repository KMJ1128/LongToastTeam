// com.longtoast.bilbil.dto/ChatRoomListDTO.kt (안드로이드)

package com.longtoast.bilbil.dto

data class ChatRoomListDTO(
    val roomId: Int?,
    val lastMessageTime: String?,

    val partnerId: Int?,
    val partnerNickname: String?,
    val partnerProfileImageUrl: String?,

    val itemId: Int?,
    val itemTitle: String?,
    val itemMainImageUrl: String?,
    val itemPrice: Int?,

    val lastMessageContent: String?,

    val unreadCount: Int? // ★ 추가됨
)