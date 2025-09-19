package com.longtoast.bilbil.dto

// 요청 바디에 토큰을 담아 서버로 보냅니다.
data class KakaoTokenRequest(
    val accessToken: String // 서버 DTO의 필드명과 일치해야 함
)