// com.longtoast.bilbil.dto/MsgEntity.kt

package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

data class MsgEntity(
    val message: String,
    // 💡 핵심: data 필드를 Any (Kotlin의 일반적인 Object 타입)로 정의하여
    // Gson이 이 필드를 Map 또는 List 등 제네릭 타입으로 파싱하도록 유도합니다.
    @SerializedName("data")
    val data: Any?
)