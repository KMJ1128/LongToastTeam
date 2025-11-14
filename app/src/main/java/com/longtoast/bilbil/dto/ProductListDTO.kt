// java/com/longtoast/bilbil/dto/ProductListDTO.kt
package com.longtoast.bilbil.dto

data class ProductListDTO(
    val itemId: Int,
    val title: String,
    val price: Int,
    val category: String,
    val status: String, // "AVAILABLE" 또는 "UNAVAILABLE"
    val mainImageUrl: String?, // 목록에서 보여줄 대표 이미지 URL
    val address: String,
    val createdAt: String // 등록 시간
)