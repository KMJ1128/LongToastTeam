package com.longtoast.bilbil.dto

// 백엔드 ChatMessage.java와 필드 및 타입 일치
data class ChatMessage(
    val id: Long,               // 백엔드 Long id (PK)
    val roomId: String,         // 백엔드 String roomId
    val senderId: String,       // 백엔드 String senderId
    val content: String?,       // 백엔드 String content (메시지 내용)
    val imageUrl: String?,      // 백엔드 String imageUrl (이미지 URL 지원)
    val sentAt: String          // 백엔드 LocalDateTime을 JSON String으로 받음 (ISO 8601)
)