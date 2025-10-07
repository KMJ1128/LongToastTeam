package com.longtoast.bilbil.dto

// 🚨 <T>를 추가하여 제네릭 클래스로 만듭니다.
data class MsgEntity<T>(
    val message: String,
    val data: T? = null, // T 타입의 데이터를 담는 필드
    val code: String? = null
)
