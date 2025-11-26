package com.longtoast.bilbil.dto

data class RentalActionPayload(
    val transactionId: Long,
    val itemId: Int,
    val startDate: String,
    val endDate: String,
    val rentFee: Int,
    val deposit: Int,
    val totalAmount: Int,
    val deliveryMethod: String
)
