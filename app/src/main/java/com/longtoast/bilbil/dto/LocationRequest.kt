package com.longtoast.bilbil.dto

data class LocationRequest(
    val userId: Int,
    val latitude: Double,
    val longitude: Double,
    val address: String
)