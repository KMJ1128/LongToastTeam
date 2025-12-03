// com.longtoast.bilbil.dto.ReviewCreateRequest.kt
package com.longtoast.bilbil.dto

data class ReviewCreateRequest(
    val transactionId: Long,
    val rating: Int,
    val comment: String
)
