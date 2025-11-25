// com.longtoast.bilbil.dto.ReviewCreateRequest.kt
package com.longtoast.bilbil.dto

data class ReviewCreateRequest(
    // 🔥 서버에서 기대하는 필드명/타입 그대로 맞춤
    val transactionId: Long,   // 백엔드 DTO의 Long transactionId 와 일치
    val rating: Int,
    val comment: String
)
