// com.longtoast.bilbil.dto.ChatRoomListUpdateDTO.kt
package com.longtoast.bilbil.dto

// 💡 [핵심 추가] 목록 업데이트 시 필요한 최소 정보 DTO
data class ChatRoomListUpdateDTO(
    val roomId: Int?,
    val partnerId: Int?, // 상대방 ID (정렬 및 UI 업데이트에 유용할 수 있음)
    val lastMessageContent: String?,
    val lastMessageTime: String?,
    val unreadCount: Int? = null
)