// com.longtoast.bilbil.dto.ChatMessage.kt
package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

/**
 * 백엔드 ChatMessage JSON 구조와 맞춰서 사용하는 DTO.
 * WebSocket 응답에는 isRead, roomId, senderId 등이 포함되고,
 * 실시간 에코 매칭을 위해 clientTempId 필드를 추가했다.
 */
data class ChatMessage(
    @SerializedName("id")
    val id: Long,

    @SerializedName(value = "roomId", alternate = ["room_id"])
    val roomId: Int,

    @SerializedName(value = "senderId", alternate = ["sender_id"])
    val senderId: Int,

    @SerializedName("content")
    val content: String?,

    @SerializedName(value = "imageUrl", alternate = ["image_url"])
    val imageUrl: String?,

    @SerializedName(value = "sentAt", alternate = ["sent_at"])
    val sentAt: String?,

    @SerializedName(value = "isRead", alternate = ["is_read"])
    val isRead: Boolean? = null,

    // ★ 로컬 임시 ID와 서버 에코 매칭용
    @SerializedName(value = "clientTempId", alternate = ["client_temp_id"])
    val clientTempId: Long? = null
)
