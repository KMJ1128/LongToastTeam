package com.longtoast.bilbil.dto

data class ChatRoomCreateRequest(
    val itemId: Int,
    val lenderId: Int,
    val borrowerId: Int
)