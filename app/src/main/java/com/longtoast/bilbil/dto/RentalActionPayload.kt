package com.longtoast.bilbil.dto

data class RentalDecisionRequest(
    val roomId: Int,          // 채팅방 ID
    val itemId: Int,
    val lenderId: Int,
    val borrowerId: Int,
    val startDate: String,
    val endDate: String,
    val totalAmount: Int
)
