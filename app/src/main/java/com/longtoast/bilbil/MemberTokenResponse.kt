package com.longtoast.bilbil

data class MemberTokenResponse(
    val id: Int,
    val nickname: String,
    val address: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?
)
