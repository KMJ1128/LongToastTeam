// com/longtoast/bilbil/dto/SearchHistoryDTO.kt
package com.longtoast.bilbil.dto

data class SearchHistoryDTO(
    val keyword: String,
    val searchedAt: String? // LocalDateTime 문자열로 들어올 거라 String 으로 받아도 충분
)
