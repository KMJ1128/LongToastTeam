// com.longtoast.bilbil.dto/ChatMsgEntity.kt

package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

// 채팅방 생성 응답 전용 DTO
data class ChatMsgEntity(
    val message: String,
    // 💡 핵심: data 필드의 타입을 ChatRoomResponse로 명시합니다.
    @SerializedName("data")
    val data: ChatRoomResponse?
)