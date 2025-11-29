package com.longtoast.bilbil.dto

data class ReviewDTO(
    val reviewId: Long,
    val transactionId: Long,
    val reviewerId: Long,
    val reviewerNickname: String?, // 리뷰 작성자 닉네임
    val rating: Int,
    val comment: String?,
    val createdAt: String?,         // 작성일 (String)
    val itemTitle: String?,         // 물품명
    val sellerNickname: String?,    // 판매자 닉네임
    val rentalPeriod: String?       // "YYYY-MM-DD ~ YYYY-MM-DD"
)