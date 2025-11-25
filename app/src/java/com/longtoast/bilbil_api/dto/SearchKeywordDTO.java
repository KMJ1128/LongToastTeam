// src/main/java/com/longtoast/bilbil_api/dto/SearchKeywordDTO.java
package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchKeywordDTO {

    private String keyword;
    private Integer viewCount;
}
