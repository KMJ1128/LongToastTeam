package com.longtoast.bilbil.dto

data class RentalApproveRequest(
    val roomId: Int,
    val itemId: Int,
    val lenderId: Int,
    val borrowerId: Int,
    val startDate: String,
    val endDate: String,
    val totalAmount: Int
)
