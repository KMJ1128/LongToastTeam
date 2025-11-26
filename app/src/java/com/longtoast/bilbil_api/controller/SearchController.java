// src/main/java/com/longtoast/bilbil_api/controller/SearchController.java
package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.SearchHistoryDTO;
import com.longtoast.bilbil_api.dto.SearchKeywordDTO;
import com.longtoast.bilbil_api.service.SearchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchLogService searchLogService;

    /**
     * 🔍 인기 검색어 상위 10개 조회
     *  - GET /search/popular
     */
    @GetMapping("/popular")
    public ResponseEntity<MsgEntity> getPopularKeywords() {
        List<SearchKeywordDTO> popular = searchLogService.getTopKeywords(); // ()만

        return ResponseEntity.ok(
                MsgEntity.builder()
                        .message("인기 검색어 조회 성공")
                        .data(popular)
                        .build()
        );
    }

    /**
     * 현재 로그인한 사용자의 최근 검색어 조회
     */
    @GetMapping("/history")
    public ResponseEntity<MsgEntity> getMySearchHistory() {
        Long userId = getCurrentUserId();
        log.info("최근 검색어 조회 요청 사용자 ID = {}", userId);

        List<SearchHistoryDTO> histories = searchLogService.getMySearchHistory(userId, 10);

        return ResponseEntity.ok(
                MsgEntity.builder()
                        .message("Recent Message View Success")
                        .data(histories)
                        .build()
        );
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }

        Object principalObject = authentication.getPrincipal();

        if (principalObject instanceof Number) {
            return ((Number) principalObject).longValue();
        }

        try {
            return Long.parseLong(principalObject.toString());
        } catch (NumberFormatException e) {
            throw new RuntimeException("유효하지 않은 사용자 정보입니다: " + principalObject, e);
        }
    }
}
