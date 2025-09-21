package com.longtoast.bilbil.dto

// 서버에서 발급한 서비스 토큰과 회원 정보가 담겨 옵니다.
data class MemberTokenResponse(
    val serviceToken: String?, // 널(null)일 경우: 아직 우리 서비스에 가입 안 됨 또는 토큰 갱신 필요
    val nickname: String,
    val userId: Long,
    val address: String?,             // 🚨 주소 필드 추가 (null 가능)
    val locationLatitude: Double?,    // 🚨 위도 필드 추가 (null 가능)
    val locationLongitude: Double?    // 🚨 경도 필드 추가 (null 가능)
)