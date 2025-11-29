package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.ItemImage;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.ProductDTO;
import com.longtoast.bilbil_api.repository.ItemImageRepository;
import com.longtoast.bilbil_api.repository.ProductsRepository;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 💡 로그 추가
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j // 💡 로그 사용을 위한 어노테이션
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadProductService {

    // 💡 프론트에서 이미지 접근할 수 있는 절대URL (꼭 / 로 끝나도 상관없도록 아래서 정규화함)
    private final String baseUrl = "http://192.168.0.211:8080/";

    private final ProductsRepository productsRepository;
    private final ItemImageRepository itemImageRepository;
    private final TransactionRepository transactionRepository;

    // ============================
    // 내가 등록한 물품 목록
    // ============================
    public List<ProductDTO> getMyItems(Long userId) {
        List<Item> myItems = productsRepository.findItemsByUserIdWithUser(userId.intValue());
        return convertListToDTO(myItems);
    }

    // ============================
    // 내가 빌린 물품 목록
    // ============================
    public List<ProductDTO> getMyRentedItems(Long userId) {
        List<Item> rentedItems =
                productsRepository.findItemsByRenterIdWithSellerAndRenter(userId.intValue());

        List<ProductDTO> dtoList = convertListToDTO(rentedItems);

        if (rentedItems.isEmpty()) return dtoList;

        try {
            List<Long> itemIds = rentedItems.stream()
                    .map(Item::getId)
                    .toList();

            List<Transaction> transactions =
                    transactionRepository.findByItem_IdInAndBorrower_Id(itemIds, userId.intValue());

            Map<Long, Long> txIdByItemId = transactions.stream()
                    .collect(Collectors.toMap(
                            tx -> tx.getItem().getId(),
                            Transaction::getId,
                            (oldVal, newVal) -> newVal
                    ));

            dtoList.forEach(dto -> dto.setTransactionId(txIdByItemId.get(dto.getId())));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dtoList;
    }

    // ============================
    // 검색 / 조건별 조회
    // ============================
    // 💡 price_unit 필터링 로직으로 변경됨
    public List<ProductDTO> getAllProducts(String category, String title, String sort, String period) {
        Sort sortCriteria = createSortCriteria(sort);

        // 💡 period(한국어)를 price_unit(숫자)로 변환하여 필터링
        Integer priceUnitFilter = convertPeriodToPriceUnit(period);

        log.info("🔥 [UNIT FILTER] Received Period: {} -> Price Unit Filter Value: {}", period, priceUnitFilter);

        // NOTE: productsRepository.findFilteredProductsWithUser의 파라미터가 priceUnitFilter로 변경되었어야 합니다.
        List<Item> items = productsRepository.findFilteredProductsWithUser(
                StringUtils.hasText(category) ? category : null,
                StringUtils.hasText(title) ? title : null,
                priceUnitFilter, // 💡 priceUnitFilter 전달
                sortCriteria
        );

        return convertListToDTO(items);
    }

    // 💡 새로운 함수: period(한국어)를 price_unit(숫자)로 변환
    private Integer convertPeriodToPriceUnit(String period) {
        if (!StringUtils.hasText(period)) return null;

        switch (period.toLowerCase()) {
            case "일":
                return 1; // price_unit = 1 (일)
            case "월":
                return 2; // price_unit = 2 (월)
            case "시간":
                return 3; // price_unit = 3 (시간)
            default:
                return null;
        }
    }

    public List<ProductDTO> getProductsBySellerId(Integer userId) {
        return convertListToDTO(productsRepository.findItemsByUserIdWithUser(userId));
    }

    public List<ProductDTO> getProductsByRenterId(Integer userId) {
        return convertListToDTO(productsRepository.findItemsByRenterIdWithSellerAndRenter(userId));
    }

    // =====================================================
    // 목록 조회용 DTO 변환 (이미지 절대경로 적용)
    // =====================================================
    private List<ProductDTO> convertListToDTO(List<Item> items) {
        if (items.isEmpty()) return List.of();

        List<ItemImage> allImages = itemImageRepository.findByItemInOrderByIsMainDesc(items);

        // 디버그 로그
        System.out.println("\n===== [DEBUG] Item 전체 이미지 매핑 =====");
        for (ItemImage img : allImages) {
            System.out.println("ItemID=" + img.getItem().getId()
                    + " / isMain=" + img.getIsMain()
                    + " / URL=" + img.getImageUrl());
        }
        System.out.println("=====================================\n");

        Map<Long, List<String>> allImageUrlsMap = allImages.stream()
                .collect(Collectors.groupingBy(
                        img -> img.getItem().getId(),
                        Collectors.mapping(ItemImage::getImageUrl, Collectors.toList())
                ));

        Map<Long, String> mainImageMap = allImages.stream()
                .filter(ItemImage::getIsMain)
                .collect(Collectors.toMap(
                        img -> img.getItem().getId(),
                        ItemImage::getImageUrl,
                        (oldVal, newVal) -> oldVal
                ));

        return items.stream()
                .map(item -> convertToDTO(
                        item,
                        mainImageMap.get(item.getId()),
                        allImageUrlsMap.get(item.getId())
                ))
                .collect(Collectors.toList());
    }

    // 💡 URL 정규화 유틸: baseUrl과 path 앞/뒤 슬래시를 안전하게 처리
    private String resolveUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // 이미 절대 URL이면 그대로 반환
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }

        // baseUrl 끝 슬래시 제거
        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        // path 앞에는 슬래시 하나만 강제
        String normalizedPath = raw.startsWith("/") ? raw : "/" + raw;

        return normalizedBase + normalizedPath;  // 예: http://...:8080 + /uploads/...
    }

    // =====================================================
    // 🔥 Item → ProductDTO 변환 (상품 이미지 + 프로필 이미지 절대경로)
    // =====================================================
    private ProductDTO convertToDTO(Item item, String mainImageUrl, List<String> imageUrls) {

        // 상대경로 → 절대경로 변환 (상품 이미지)
        String resolvedMain = resolveUrl(mainImageUrl);

        List<String> resolvedList = (imageUrls != null)
                ? imageUrls.stream()
                .map(this::resolveUrl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                : List.of();

        if (resolvedMain == null && !resolvedList.isEmpty()) {
            resolvedMain = resolvedList.get(0);
        }

        // ✅ 판매자 프로필 이미지 URL 생성 (같은 resolve 로직 사용)
        User seller = item.getUser();
        String sellerProfileImageUrl = null;
        String rawProfile = null;

        if (seller != null) {
            rawProfile = seller.getProfileImageUrl();   // "/uploads/profile/..." 형식
            sellerProfileImageUrl = resolveUrl(rawProfile);
        }

        // 디버그 로그
        System.out.println("[PROFILE] itemId=" + item.getId()
                + ", sellerId=" + (seller != null ? seller.getId() : null)
                + ", rawProfile=" + rawProfile
                + ", resolvedProfile=" + sellerProfileImageUrl);

        return ProductDTO.builder()
                .id(item.getId())
                .title(item.getTitle())
                .price(item.getPrice())
                .price_unit(item.getPrice_unit())
                .description(item.getDescription())
                .category(item.getCategory())
                .deposit(item.getDeposit())
                .tradeLocation(item.getTradeLocation())
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .address(item.getTradeLocation())
                .status(item.getStatus())
                .created_at(item.getCreatedAt())
                .imageUrl(resolvedMain)
                .imageUrls(resolvedList)
                .sellerId(seller != null ? seller.getId() : null)
                .sellerNickname(seller != null ? seller.getNickname() : null)
                .sellerCreditScore(seller != null ? seller.getCreditScore() : 0)
                .sellerProfileImageUrl(sellerProfileImageUrl)
                .build();
    }

    // =====================================================
    // ⭐ 상품 상세 조회 — 절대 URL + 디버그 로그
    // =====================================================
    public ProductDTO getProductDetail(Integer itemId) {
        Item item = productsRepository.findItemWithUserById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("상품 없음: " + itemId));

        String main = itemImageRepository.findByItemAndIsMain(item, true)
                .map(ItemImage::getImageUrl)
                .orElse(null);

        List<String> allList = itemImageRepository.findByItemOrderByIsMainDesc(item)
                .stream()
                .map(ItemImage::getImageUrl)
                .collect(Collectors.toList());

        System.out.println("=== DETAIL IMAGE DEBUG ===");
        System.out.println("mainImage = " + main);
        System.out.println("allImages = " + allList);
        System.out.println("==========================");

        return convertToDTO(item, main, allList);
    }

    private Sort createSortCriteria(String sort) {
        if (!StringUtils.hasText(sort)) return Sort.unsorted();

        // 💡 클라이언트의 토글 상태를 지원하도록 확장
        switch (sort.toLowerCase()) {
            // 가격순 토글
            case "price_low":       // 가격 낮은 순 (기존 lowest/price_asc)
            case "low":
            case "low_price":
                return Sort.by(Sort.Direction.ASC, "price");
            case "price_high":      // 가격 높은 순 (기존 highest/price_desc)
            case "high":
            case "high_price":
                return Sort.by(Sort.Direction.DESC, "price");

            // 시간순 토글
            case "latest":          // 최신순 (기존 created_desc)
            case "newest":
                return Sort.by(Sort.Direction.DESC, "createdAt");
            case "oldest":          // 오래된 순
                return Sort.by(Sort.Direction.ASC, "createdAt");

            default: return Sort.unsorted();
        }
    }
}