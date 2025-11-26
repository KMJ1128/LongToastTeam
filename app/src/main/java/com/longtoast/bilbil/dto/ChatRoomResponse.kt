package com.longtoast.bilbil.dto

data class ChatRoomResponse(
    // 백엔드에서 INT roomId를 반환하므로 Int로 수신한다.
    val roomId: Int?,
)
