package com.longtoast.bilbil_api.dto;

import com.longtoast.bilbil_api.domain.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// 안드로이드 ProductCreateRequest.kt의 필드와 동일하게 맞춥니다.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateRequest {

    private String title;
    private int price;
    private int price_unit;
    private String description;
    private String category;

    // 💡 [수정] Item.Status ENUM 타입으로 변경 (클라이언트에서는 String으로 전송)
    private Item.Status status;

    private Integer deposit;
    private String address;
}
