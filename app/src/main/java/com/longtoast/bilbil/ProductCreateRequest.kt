// com.longtoast.bilbil.dto/ProductCreateRequest.kt (전체)
package com.longtoast.bilbil.dto

// 서버에 게시글 작성을 위해 보낼 데이터를 정의합니다.
data class ProductCreateRequest(
    val title: String,
    val price: Int,
    val description: String,
    val category: String,
    val status: String,      // "AVAILABLE" 또는 "UNAVAILABLE"
    val deposit: Int?,       // 보증금 (필수 아님: Nullable Int)
    val address: String
)