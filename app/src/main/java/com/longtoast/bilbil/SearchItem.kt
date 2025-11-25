package com.longtoast.bilbil

data class SearchItem(
    val name: String,
    val address: String, // 🚨 [유지] 주소 필드 추가
    val latitude: Double,
    val longitude: Double
)