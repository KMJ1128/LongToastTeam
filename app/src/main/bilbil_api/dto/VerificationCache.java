package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class VerificationCache {
    private String phoneNumber;
    private String code;
    private LocalDateTime expiryTime;
}