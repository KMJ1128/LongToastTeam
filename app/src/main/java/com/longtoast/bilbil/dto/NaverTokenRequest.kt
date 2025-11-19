// java/com/longtoast/bilbil/dto/NaverTokenRequest.kt
package com.longtoast.bilbil.dto

// Naver Access Token을 서버로 보냅니다.
data class NaverTokenRequest(
    val accessToken: String // 서버 DTO의 필드명과 일치해야 함
)