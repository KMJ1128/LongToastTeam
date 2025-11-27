// com.longtoast.bilbil.dto.ProductDTO.kt
package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

data class ProductDTO(
    // 1. 판매자 정보 (백엔드 변수명과 매칭)
    @SerializedName("sellerId")
    val userId: Int,           // 안드로이드에선 userId로 쓰되, JSON의 sellerId와 매핑

    val sellerNickname: String?,    // 백엔드에 추가된 필드
    val sellerCreditScore: Int?,    // 백엔드에 추가된 필드

    // 2. 물품 공통 정보
    val id: Int,               // 백엔드 Long -> 안드로이드 Int (범위 내라면 호환 가능)
    val title: String,
    val price: Int,
    val price_unit:Int,
    val category: String?,

    // 3. 상세 정보
    val description: String?,
    val deposit: Int?,
    val tradeLocation: String?, // 백엔드에 있는 거래 위치
    val address: String?,       // 백엔드에 있는 주소
    val latitude: Double?,
    val longitude: Double?,

    // 🚨 [핵심] 이미지 리스트 (Base64 문자열 리스트)
    val imageUrls: List<String>?,

    // 4. 상태 및 시간
    val status: String?,        // 백엔드 Enum -> String으로 받음

    @SerializedName("created_at") // 백엔드 변수명이 created_at (스네이크 표기법)일 경우 매핑
    val createdAt: String?,

    @SerializedName("transactionId")
    val transactionId: Long? = null
)