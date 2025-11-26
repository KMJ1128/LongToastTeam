// src/main/java/com/longtoast/bilbil_api/repository/UserSearchHistoryRepository.java
package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.UserSearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, Long> {

    /**
     * ✅ 한 유저에 대해
     *    같은 keyword 중에서 가장 최근(최신 searchedAt) 기록만 남기고,
     *    그 서로 다른 키워드들을 최신순으로 가져오는 쿼리
     *
     *  - 예: user_id = 1 이 "가방"을 3번, "신발"을 2번 검색했으면
     *    → "가방"의 가장 최근 1개 + "신발"의 가장 최근 1개만 반환
     */
    @Query("""
        SELECT h
        FROM UserSearchHistory h
        WHERE h.user.id = :userId
          AND h.searchedAt = (
              SELECT MAX(h2.searchedAt)
              FROM UserSearchHistory h2
              WHERE h2.user.id = :userId
                AND h2.keyword = h.keyword
          )
        ORDER BY h.searchedAt DESC
        """)
    List<UserSearchHistory> findLatestDistinctByUser(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
