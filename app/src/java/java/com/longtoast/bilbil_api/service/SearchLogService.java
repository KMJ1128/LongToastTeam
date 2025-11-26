// src/main/java/com/longtoast/bilbil_api/service/SearchLogService.java
package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.SearchLog;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.domain.UserSearchHistory;
import com.longtoast.bilbil_api.dto.SearchHistoryDTO;
import com.longtoast.bilbil_api.dto.SearchKeywordDTO;
import com.longtoast.bilbil_api.repository.SearchLogRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import com.longtoast.bilbil_api.repository.UserSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;
    private final UserSearchHistoryRepository userSearchHistoryRepository;
    private final UserRepository userRepository;

    /**
     * ✅ 상품 검색 시 호출되는 메서드
     *   - 전역 인기 검색어(search_logs)에 view_count 증가
     *   - 로그인된 사용자는 user_search_history 에도 기록
     */
    @Transactional
    public void logKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        String trimmed = keyword.trim();
        log.debug("검색어 로그 기록 시도: {}", trimmed);

        // 1) 전역 인기 검색어: view_count 증가
        SearchLog searchLog = searchLogRepository.findByKeyword(trimmed)
                .orElseGet(() -> SearchLog.builder()
                        .keyword(trimmed)
                        .viewCount(0)
                        .build());

        searchLog.increaseViewCount(); // 또는 setViewCount(searchLog.getViewCount() + 1);
        searchLogRepository.save(searchLog);

        // 2) 로그인한 유저라면 user_search_history 에도 추가
        Long currentUserId = getCurrentUserIdOrNull();
        if (currentUserId == null) {
            return;
        }

        Integer userIdInt = currentUserId.intValue();

        userRepository.findById(userIdInt)
                .ifPresent(user -> {
                    UserSearchHistory history = UserSearchHistory.builder()
                            .user(user)
                            .keyword(trimmed)
                            .build();
                    userSearchHistoryRepository.save(history);
                });
    }

    /**
     * ✅ 전역 인기 검색어 Top N 조회
     *   - /search/popular 에서 사용
     */
    @Transactional(readOnly = true)
    public List<SearchKeywordDTO> getTopKeywords() {
        // 지금 레포지토리는 Top10만 주니까, limit은 무시하거나 주석으로 표시해두자
        List<SearchLog> logs = searchLogRepository.findTop10ByOrderByViewCountDesc();

        return logs.stream()
                .map(log -> SearchKeywordDTO.builder()
                        .keyword(log.getKeyword())
                        .viewCount(log.getViewCount())
                        .build())
                .toList();
    }

    /**
     * ✅ 현재 로그인한 사용자의 최근 검색어 (서로 다른 키워드만) 조회
     *   - /search/history 에서 사용
     *   - 같은 검색어를 여러 번 검색해도,
     *     "가장 최근에 검색한 1건"만 남도록 Repo 쿼리에서 정리
     */
    @Transactional(readOnly = true)
    public List<SearchHistoryDTO> getMySearchHistory(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);

        // 중복 허용 X, 서로 다른 검색어의 최신 기록만 조회
        List<UserSearchHistory> histories =
                userSearchHistoryRepository.findLatestDistinctByUser(userId, pageable);

        return histories.stream()
                .map(SearchHistoryDTO::from)
                .toList();
    }

    /**
     * 🔐 현재 로그인한 사용자 ID (Long) 또는 null 반환
     *   - logKeyword 에서 "로그인 안 했으면 user_search_history 기록 안 함" 용으로 사용
     */
    private Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        Object principalObject = authentication.getPrincipal();

        if (principalObject instanceof Number) {
            return ((Number) principalObject).longValue();
        }

        try {
            return Long.parseLong(principalObject.toString());
        } catch (NumberFormatException e) {
            log.warn("검색 히스토리 기록 시 유효하지 않은 사용자 정보: {}", principalObject);
            return null;
        }
    }
}
