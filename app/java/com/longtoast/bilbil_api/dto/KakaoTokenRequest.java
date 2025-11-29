package com.longtoast.bilbil_api.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KakaoTokenRequest {
    // 안드로이드 앱에서 받은 카카오 Access Token을 담음.d
    private String accessToken;
}