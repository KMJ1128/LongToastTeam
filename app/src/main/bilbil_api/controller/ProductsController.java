package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.ProductDTO;
import com.longtoast.bilbil_api.service.ReadProductService;
import com.longtoast.bilbil_api.service.SearchLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductsController {

    private final ReadProductService readProductService;
    private final SearchLogService searchLogService;


    /**
     * 물품 목록 조회 엔드포인트: category, title, sort, period 파라미터를 모두 받음 (수정됨)
     * 예시: GET /products/lists?category=FASHION&title=가방&period=월
     */
    @GetMapping("/lists")
    public ResponseEntity<MsgEntity> getProducts(
            HttpServletRequest request,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sort", defaultValue = "") String sort,
            // 💡 period 파라미터 추가
            @RequestParam(value = "period", required = false) String period
    ) throws Exception {

        // 🔥 추가: 공백 문자열은 null 로 바꿔야 정상 검색됨
        if (category != null && category.trim().isEmpty()) category = null;
        if (title != null && title.trim().isEmpty()) title = null;
        // 💡 period도 공백이면 null 처리
        if (period != null && period.trim().isEmpty()) period = null;


        // 검색 로그 처리
        String keywordForLog = null;

        if (title != null) keywordForLog = title;
        else if (category != null) keywordForLog = category;

        if (keywordForLog != null) {
            searchLogService.logKeyword(keywordForLog);
        }

        // 💡 period 파라미터 전달
        List<ProductDTO> productLists = readProductService.getAllProducts(category, title, sort, period);

        return ResponseEntity.ok()
                .body(new MsgEntity("상품 목록 조회 성공", productLists));
    }

    /**
     * (판매자 기준) 특정 사용자가 올린 물품 목록 조회 엔드포인트: PathVariable로 사용자 ID를 받음
     * 예시: GET /products/seller/1
     */
    @GetMapping("/seller/{userId}")
    public ResponseEntity<MsgEntity> getProductsBySellerId(@PathVariable Integer userId) {

        List<ProductDTO> productLists = readProductService.getProductsBySellerId(userId);

        return ResponseEntity.ok()
                .body(new MsgEntity("사용자 ID: " + userId + " 가 등록한 상품 목록 조회 성공", productLists));
    }


    /**
     * 💡 (대여자 기준) 특정 사용자가 대여한 물품 목록 조회 엔드포인트: PathVariable로 사용자 ID를 받음
     * 예시: GET /products/renter/1
     */
    @GetMapping("/renter/{userId}")
    public ResponseEntity<MsgEntity> getProductsByRenterId(@PathVariable Integer userId) {

        List<ProductDTO> productLists = readProductService.getProductsByRenterId(userId);

        return ResponseEntity.ok()
                .body(new MsgEntity("사용자 ID: " + userId + " 가 대여한 상품 목록 조회 성공", productLists));
    }


    /**
     * 물품 상세 보기 엔드포인트: ProductDTO를 반환
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<MsgEntity> getProductDetail(@PathVariable Integer itemId) throws Exception {

        ProductDTO response = readProductService.getProductDetail(itemId);

        return ResponseEntity.ok()
                .body(new MsgEntity("물품 상세 정보 조회 성공", response));
    }


    // ===========================
    // 1) 내가 등록한 물품 목록
    // ===========================
    @GetMapping("/myitems")
    public ResponseEntity<MsgEntity> getMyItems() {
        Long userId = getCurrentUserId();
        log.info("🔥 [MY ITEMS] 요청한 사용자 ID = {}", userId);

        List<ProductDTO> myItems = readProductService.getMyItems(userId);
        log.info("🔥 [MY ITEMS] 반환된 아이템 개수 = {}", myItems.size());
        log.info("요청 사용자 ID(등록한 물품 조회): {}", userId);

        try {
            return ResponseEntity.ok(
                    MsgEntity.builder()
                            .message("내가 등록한 상품 목록 조회 성공")
                            .data(myItems)   // List<ProductDTO>
                            .build()
            );
        } catch (Exception e) {
            log.error("Error fetching my items for user {}", userId, e);
            return ResponseEntity.status(500).body(
                    MsgEntity.builder()
                            .message("서버 오류로 상품 목록을 불러올 수 없습니다.")
                            .build()
            );
        }
    }

    // ===========================
    // 2) 내가 렌트한 물품 목록
    // ===========================
    @GetMapping("/myrentals")
    public ResponseEntity<MsgEntity> getMyRentals() {
        Long userId = getCurrentUserId();
        log.info("요청 사용자 ID(렌트한 물품 조회): {}", userId);

        try {
            List<ProductDTO> myRentals = readProductService.getMyRentedItems(userId);

            return ResponseEntity.ok(
                    MsgEntity.builder()
                            .message("내가 렌트한 상품 목록 조회 성공")
                            .data(myRentals)   // List<ProductDTO>
                            .build()
            );
        } catch (Exception e) {
            log.error("Error fetching my rentals for user {}", userId, e);
            return ResponseEntity.status(500).body(
                    MsgEntity.builder()
                            .message("서버 오류로 렌트한 상품 목록을 불러올 수 없습니다.")
                            .build()
            );
        }
    }

    // 🔐 공통 메서드: 현재 로그인한 사용자 ID 추출
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