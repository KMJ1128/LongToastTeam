package com.longtoast.bilbil.dto

data class VerifyRequest(
    val phoneNumber: String
    // 필요하다면 val code: String? 도 추가할 수 있으나, 현재 서버 로직에서는 phoneNumber만 보냅니다.
)
