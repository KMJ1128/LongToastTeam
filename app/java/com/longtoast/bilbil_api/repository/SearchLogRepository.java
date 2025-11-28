// src/main/java/com/longtoast/bilbil_api/repository/SearchLogRepository.java
package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    Optional<SearchLog> findByKeyword(String keyword);

    // 상위 10개 인기 검색어
    List<SearchLog> findTop10ByOrderByViewCountDesc();
}
