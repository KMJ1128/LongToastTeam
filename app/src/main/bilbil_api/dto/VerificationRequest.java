package com.longtoast.bilbil_api.dto;

import lombok.Data;

// 안드로이드 VerifyRequest DTO와 동일한 역할
@Data
public class VerificationRequest {
    private String phoneNumber;
}