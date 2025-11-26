// src/main/java/com/longtoast/bilbil_api/domain/SearchLog.java
package com.longtoast.bilbil_api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "search_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String keyword;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 1;

    // 🔥 viewCount 1 증가시키는 편의 메서드
    public void increaseViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 1;
        } else {
            this.viewCount = this.viewCount + 1;
        }
    }
}
