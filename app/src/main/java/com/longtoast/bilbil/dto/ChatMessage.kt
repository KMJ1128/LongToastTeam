// com.longtoast.bilbil.dto.ChatMessage.kt
package com.longtoast.bilbil.dto

// 백엔드 ChatMessage.java와 필드 및 타입 일치
// 서버에서 roomId를 제공하지 않는 경우가 있어 모든 필드를 nullable + 기본값으로 둔다.
data class ChatMessage(
    val id: Long? = null,
    val roomId: String? = null,
    val senderId: Int? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val sentAt: String? = null
)
