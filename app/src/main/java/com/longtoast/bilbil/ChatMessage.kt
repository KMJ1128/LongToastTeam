// com.longtoast.bilbil.dto.ChatMessage.kt
package com.longtoast.bilbil.dto

// 백엔드 ChatMessage.java와 필드 및 타입 일치
data class ChatMessage(
    val id: Long,
    val roomId: Int,
    val senderId: Int,
    val content: String?,
    val imageUrl: String?,
    val sentAt: String?
)