package com.longtoast.bilbil.dto

// 백엔드 ChatMessage.java와 필드 및 타입 일치
// roomId는 DB/엔드포인트에서 INT로 전달되므로 Int로 맞춘다.
data class ChatMessage(
    val id: Long,
    val roomId: Int,
    val senderId: Int,
    val content: String?,
    val imageUrl: String?,
    val sentAt: String,
    val isRead: Boolean? = false// ★ 추가됨
)
