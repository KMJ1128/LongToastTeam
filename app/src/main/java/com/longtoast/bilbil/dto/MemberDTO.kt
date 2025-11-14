// com.longtoast.bilbil.dto.MemberDTO.kt
package com.longtoast.bilbil.dto

// 🚨 [핵심 수정] java.time.LocalDateTime 임포트를 제거하고 String?으로 대체합니다.

/**
 * [회원 정보 업데이트 요청 DTO]
 * 백엔드 MemberController가 요구하는 형태를 따르지만,
 * 안드로이드 호환성을 위해 날짜/시간 필드는 String으로 처리합니다.
 */
data class MemberDTO(
    // 1. ID 및 닉네임 (필수 업데이트 항목)
    val id: Int,
    val nickname: String,

    // 2. 주소 및 위치 정보 (업데이트 항목)
    val address: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,

    // 3. 나머지 백엔드 MemberDTO 필드 (값 유지 또는 더미 처리)
    // 💡 백엔드 DTO의 필드 순서와 타입을 맞추기 위해 모두 포함
    val creditScore: Int?,
    val profileImageUrl: String?,
    // 🚨 [수정] LocalDateTime 대신 String?으로 대체
    val createdAt: String?
)