package com.longtoast.bilbil_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder           // ✅ builder() 메서드 생성
@NoArgsConstructor // ✅ 기본 생성자 생성 (Builder 사용 시 필수)
@AllArgsConstructor
public class MsgEntity {
    private String message;
    private Object data; // 응답 본문에 담길 실제 데이터 (KakaoDTO 등) d
}