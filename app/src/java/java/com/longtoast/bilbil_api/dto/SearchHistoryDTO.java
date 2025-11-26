// src/main/java/com/longtoast/bilbil_api/dto/SearchHistoryDTO.java
package com.longtoast.bilbil_api.dto;

import com.longtoast.bilbil_api.domain.UserSearchHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SearchHistoryDTO {

    private String keyword;
    private LocalDateTime searchedAt;

    public static SearchHistoryDTO from(UserSearchHistory history) {
        return SearchHistoryDTO.builder()
                .keyword(history.getKeyword())
                .searchedAt(history.getSearchedAt())
                .build();
    }
}
