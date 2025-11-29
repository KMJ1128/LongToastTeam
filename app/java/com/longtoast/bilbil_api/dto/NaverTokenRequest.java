package com.longtoast.bilbil_api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NaverTokenRequest {
    // 안드로이드에서 보낸 필드명 (accessToken)과 일치해야 합니다.
    private String accessToken;
}