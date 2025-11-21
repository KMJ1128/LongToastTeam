// com.longtoast.bilbil.dto/ProductDTO.kt (안드로이드)

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
    // 🚨 [핵심 수정] 서버에서 전송하는 Base64 문자열 리스트를 받을 필드 추가
    val imageUrls: List<String>?,
    // 기존의 mainImageUrl은 이제 imageUrls의 첫 번째 요소가 됩니다.
    val mainImageUrl: String? = null // 기존 필드 유지 (호환성)
)