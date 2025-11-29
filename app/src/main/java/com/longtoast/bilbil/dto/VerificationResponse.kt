package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

/**
 * [전화번호 인증 요청 응답 DTO]
 * 서버의 VerificationResponse.java 구조와 동일하게 작성해야 합니다.
 */
data class VerificationResponse(
    @SerializedName("smsUrl")
    val smsUrl: String?, // 클라이언트가 문자를 보내야 할 완성된 URI (예: sms:service@email.com?body=...)

    @SerializedName("verificationCode")
    val verificationCode: String?, // 서버가 생성한 인증 코드 (디버깅 또는 상태 표시용)

    @SerializedName("recipientEmail")
    val recipientEmail: String? // 문자를 보내야 할 수신 이메일 주소
)