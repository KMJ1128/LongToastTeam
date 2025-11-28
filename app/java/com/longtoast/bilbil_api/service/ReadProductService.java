package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.config.ServerConfig;
import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.ItemImage;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.dto.ProductDTO;
import com.longtoast.bilbil_api.repository.ItemImageRepository;
import com.longtoast.bilbil_api.repository.ProductsRepository;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadProductService {

    // 💡 프론트에서 이미지 접근할 수 있는 절대URL
    private final String baseUrl = "http://172.16.102.219:8080";

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
    public List<ProductDTO> getAllProducts(String category, String title, String sort) {
        Sort sortCriteria = createSortCriteria(sort);

        List<Item> items = productsRepository.findFilteredProductsWithUser(
                StringUtils.hasText(category) ? category : null,
                StringUtils.hasText(title) ? title : null,
                sortCriteria
        );

        return convertListToDTO(items);
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

    // =====================================================
    // Item → ProductDTO 변환 (이미지 절대경로 포함)
    // =====================================================
    private ProductDTO convertToDTO(Item item, String mainImageUrl, List<String> imageUrls) {

        // 상대경로 → 절대경로 변환
        String resolvedMain = (mainImageUrl != null) ? (baseUrl + mainImageUrl) : null;

        List<String> resolvedList = (imageUrls != null)
                ? imageUrls.stream().map(url -> baseUrl + url).collect(Collectors.toList())
                : List.of();

        if (resolvedMain == null && !resolvedList.isEmpty()) {
            resolvedMain = resolvedList.get(0);
        }

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
                .sellerId(item.getUser() != null ? item.getUser().getId() : null)
                .sellerNickname(item.getUser() != null ? item.getUser().getNickname() : null)
                .sellerCreditScore(item.getUser() != null ? item.getUser().getCreditScore() : 0)
                .build();
    }

    // =====================================================
    // ⭐ 상품 상세 조회 — 절대 URL + 디버그 로그 추가됨
    // =====================================================
    public ProductDTO getProductDetail(Integer itemId) {
        Item item = productsRepository.findItemWithUserById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("상품 없음: " + itemId));

        // DB에서 이미지 모두 가져오기
        String main = itemImageRepository.findByItemAndIsMain(item, true)
                .map(ItemImage::getImageUrl)
                .orElse(null);

        List<String> allList = itemImageRepository.findByItemOrderByIsMainDesc(item)
                .stream()
                .map(ItemImage::getImageUrl)
                .collect(Collectors.toList());

        // 디버그 로그
        System.out.println("=== DETAIL IMAGE DEBUG ===");
        System.out.println("mainImage = " + main);
        System.out.println("allImages = " + allList);
        System.out.println("==========================");

        return convertToDTO(item, main, allList);
    }

    private Sort createSortCriteria(String sort) {
        if (!StringUtils.hasText(sort)) return Sort.unsorted();

        switch (sort.toLowerCase()) {
            case "lowest":
            case "price_asc": return Sort.by(Sort.Direction.ASC, "price");
            case "highest":
            case "price_desc": return Sort.by(Sort.Direction.DESC, "price");
            case "latest":
            case "created_desc": return Sort.by(Sort.Direction.DESC, "createdAt");
            default: return Sort.unsorted();
        }
    }
}
