// com.longtoast.bilbil.dto.ProductDTO.kt
package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

data class ProductDTO(
    // 1. 판매자 정보 (백엔드 변수명과 매칭)
    @SerializedName("sellerId")
    val userId: Int,                 // 안드로이드에서는 userId로 사용

    val sellerNickname: String?,     // 판매자 닉네임
    val sellerCreditScore: Int?,     // 판매자 신용점수

    @SerializedName("sellerProfileImageUrl")
    val sellerProfileImageUrl: String?,   // 🔥 새로 추가된 프로필 이미지 URL

    // 2. 물품 공통 정보
    val id: Int,
    val title: String,
    val price: Int,
    val price_unit: Int,
    val category: String?,

    // 3. 상세 정보
    val description: String?,
    val deposit: Int?,
    val tradeLocation: String?, // 백엔드 tradeLocation
    val address: String?,       // 백엔드 address
    val latitude: Double?,
    val longitude: Double?,

    // 이미지 리스트 (상세/슬라이더용)
    val imageUrls: List<String>?,

    // 4. 상태 및 시간
    val status: String?,        // Enum을 문자열로 받음

    @SerializedName("created_at")
    val createdAt: String?,     // "yyyy-MM-dd'T'HH:mm:ss" 형태 예상

    @SerializedName("transactionId")
    val transactionId: Long? = null,

    @SerializedName("reservedPeriods")
    val reservedPeriods: List<String>? = emptyList()


)
