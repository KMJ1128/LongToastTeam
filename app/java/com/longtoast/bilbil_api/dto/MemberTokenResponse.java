package com.longtoast.bilbil_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class MemberTokenResponse {
    // 우리 서비스가 발행한 인증 토큰 (예: JWT) d
    private String serviceToken;
    private int userId;
    // 로그인된 회원의 기본 정보
    private String nickname;
    // 주소 관련 필드 추가 (안드로이드에서 null 여부 확인용)
    private String address;             // String 주소 (null 가능)
    private Double locationLatitude;    // 위도 (null 가능)
    private Double locationLongitude;   // 경도 (null 가능
    private int creditScore;
    private String profileImageUrl;
}