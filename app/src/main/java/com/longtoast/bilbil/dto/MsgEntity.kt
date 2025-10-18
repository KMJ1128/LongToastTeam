package com.longtoast.bilbil.dto

// 🚨 <T>를 추가하여 제네릭 클래스로 만듭니다.
data class MsgEntity(
    val success: Boolean,
    val message: String,
    // data 필드는 MemberTokenResponse 타입 또는 null을 가질 수 있습니다.
    val data: MemberTokenResponse?
)
