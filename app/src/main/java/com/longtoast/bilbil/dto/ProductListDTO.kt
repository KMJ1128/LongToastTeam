package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

data class ProductListDTO(
    val id: Int,
    val title: String,
    val description: String?,
    val price: Int,
    val price_unit: Int,
    val category: String?,
    val address: String?,
    val status: String,

    @SerializedName("imageUrl")
    val imageUrl: String?,        // 서버 필드명과 동일

    @SerializedName("imageUrls")
    val imageUrls: List<String>?  // 상세/여러 장 이미지도 받을 수 있도록 추가
)
