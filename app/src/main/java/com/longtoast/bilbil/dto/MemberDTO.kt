// com.longtoast.bilbil.dto.MemberDTO.kt (전체)
package com.longtoast.bilbil.dto

/**
 * [회원 정보 업데이트 요청 DTO]
 * 백엔드 MemberController가 요구하는 형태를 따르며,
 * 안드로이드 호환성을 위해 날짜/시간 필드는 String으로 처리합니다.
 */
data class MemberDTO(
    // 1. ID 및 닉네임 (필수 업데이트 항목)
    val id: Int,
    val nickname: String,

    // 💡 [핵심 추가] 백엔드 DTO와의 통일성을 위해 username 필드 추가
    val username: String?,

    // 2. 주소 및 위치 정보 (업데이트 항목)
    val address: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,

    // 3. 나머지 백엔드 MemberDTO 필드 (값 유지 또는 더미 처리)
    val creditScore: Int?,
    val profileImageUrl: String?,
    val createdAt: String?
)