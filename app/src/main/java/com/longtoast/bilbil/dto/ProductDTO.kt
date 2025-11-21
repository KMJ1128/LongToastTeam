package com.longtoast.bilbil.dto

data class ProductDTO(
    val id: Int,
    val userId: Int?,           // 판매자 ID
    val renterId: Int?,         // 대여자 ID (없으면 null)
    val title: String?,
    val description: String?,
    val price: Int?,
    val deposit: Int?,
    val address: String?,
    val category: String?,
    val status: String?,        // AVAILABLE, RESERVED, RENTED, UNAVAILABLE
    val createdAt: String?,
    val mainImageUrl: String?   // 대표 이미지
)
