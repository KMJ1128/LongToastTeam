package com.longtoast.bilbil.dto

// 서버에서 발급한 서비스 토큰과 회원 정보가 담겨 옵니다.
data class MemberTokenResponse(
    val serviceToken: String, // 우리 서비스가 발행한 토큰
    val nickname: String,
    val email: String
)