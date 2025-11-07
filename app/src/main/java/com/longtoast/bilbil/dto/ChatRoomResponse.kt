// com.longtoast.bilbil.dto/ChatRoomResponse.kt

package com.longtoast.bilbil.dto

data class ChatRoomResponse(
    // 백엔드에서 String으로 보냈으므로 String으로 받습니다.
    val roomId: String?
)