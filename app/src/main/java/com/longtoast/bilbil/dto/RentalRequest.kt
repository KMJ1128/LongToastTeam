package com.longtoast.bilbil.dto

data class RentalRequest(
    val itemId: Int,
    val lenderId: Int,
    val borrowerId: Int,
    val startDate: String,
    val endDate: String,
    val rentFee: Int,
    val deposit: Int,
    val totalAmount: Int,
    val deliveryMethod: String
)
