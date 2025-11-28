// com.longtoast.bilbil.dto/ProductCreateRequest.kt (전체)
package com.longtoast.bilbil.dto

// 서버에 게시글 작성을 위해 보낼 데이터를 정의합니다.
data class ProductCreateRequest(
    val title: String,
    val price: Int,
    val price_unit: Int,
    val description: String,
    val category: String,
    val status: String,
    val deposit: Int?,
    val imageUrls: List<String>,
    val address: String,
    val latitude: Double,      // 추가됨
    val longitude: Double      // 추가됨
)