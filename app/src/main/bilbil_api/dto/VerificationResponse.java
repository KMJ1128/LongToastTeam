package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerificationResponse {
    // 클라이언트가 문자를 보내야 할 완전한 URI (예: sms:service@email.com?body=...)
    private String smsUrl;
    // (옵션) 클라이언트가 재인증 시 사용할 수 있도록 서버에서 생성한 인증 코드
    private String verificationCode;
    // (옵션) 문자를 보내야 할 수신 이메일 주소
    private String recipientEmail;
}