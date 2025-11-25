// com.longtoast.bilbil.dto.ChatMessage.kt
package com.longtoast.bilbil.dto

/**
 * 백엔드 ChatMessage JSON 구조와 맞춰서 사용하는 DTO.
 * WebSocket 응답에는 isRead, roomId, senderId 등이 포함되고,
 * 실시간 에코 매칭을 위해 clientTempId 필드를 추가했다.
 */
data class ChatMessage(
    val id: Long,
    val roomId: Int,
    val senderId: Int,
    val content: String?,
    val imageUrl: String?,
    val sentAt: String?,
    val isRead: Boolean? = null,
    val clientTempId: Long? = null // ★ 로컬 임시 ID와 서버 에코 매칭용
)
