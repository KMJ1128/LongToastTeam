// com.longtoast.bilbil.dto.LenderReviewTargetDTO.kt
package com.longtoast.bilbil.dto

data class LenderReviewTargetDTO(
    val rentalId: Long,          // = Transaction.id
    val borrowerId: Long,
    val borrowerNickname: String,
    val itemTitle: String,
    val rentalPeriod: String?    // 예) "2025-01-01 ~ 2025-01-03"
)
