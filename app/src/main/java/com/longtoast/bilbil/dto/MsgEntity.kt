package com.longtoast.bilbil.dto

// 모든 API 응답의 기본 구조
data class MsgEntity(
    val success: Boolean,
    val message: String,
    // data 필드는 MemberTokenResponse 타입 또는 null을 가질 수 있습니다.
    val data: MemberTokenResponse?
)