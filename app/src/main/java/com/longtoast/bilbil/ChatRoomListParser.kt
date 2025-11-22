package com.longtoast.bilbil

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.longtoast.bilbil.dto.MsgEntity

/**
 * 공통으로 사용하는 채팅방 목록 파서.
 * REST 응답(body.data)을 안전하게 [ChatRoomListDTO] 리스트로 변환한다.
 */
object ChatRoomListParser {

    private val gson = Gson()
    private val listType = object : TypeToken<List<ChatRoomListDTO>>() {}.type

    fun parseFromMsgEntity(entity: MsgEntity?): List<ChatRoomListDTO> {
        if (entity?.data == null) return emptyList()

        return try {
            val dataJson = gson.toJson(entity.data)
            gson.fromJson<List<ChatRoomListDTO>>(dataJson, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e("CHAT_LIST_PARSE", "목록 파싱 오류", e)
            emptyList()
        }
    }
}
