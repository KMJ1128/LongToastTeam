// com.longtoast.bilbil.dto/ChatRoomResponse.kt

package com.longtoast.bilbil.dto

// 백엔드 ChatRoomController가 Map으로 반환하는 데이터를 위한 DTO입니다.
// 백엔드에서 roomId를 String으로 보냈으므로, 여기도 String으로 받습니다.
data class ChatRoomResponse(
    val roomId: String?
)