package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductsRepository extends JpaRepository<Item, Integer> {

    // ----------------------------------------------------
    // ✅ 1. '내 활동' 목록 조회 (Fetch Join으로 User 정보 강제 로딩)
    // Long userId를 사용하도록 명확하게 정의
    // ----------------------------------------------------
    @Query("SELECT i FROM Item i JOIN FETCH i.user u WHERE i.user.id = :userId")
    List<Item> findMyItemsWithUser(@Param("userId") Long userId);

    // 2. 기존 메서드 유지
    Optional<Item> findItemById(Integer id);
    Optional<Item> findByUser_Id(Integer id);

    // ----------------------------------------------------
    // ✅ 카테고리/제목 검색 및 동적 정렬을 지원하는 Fetch Join 쿼리
    //  - category가 넘어오면: i.category = :category 로 필터
    //  - title이 넘어오면: i.title LIKE %title% 로 제목에만 검색
    //  - 둘 다 null이면 전체 조회
    // ----------------------------------------------------
    @Query(
            "SELECT i FROM Item i JOIN FETCH i.user u " +
                    "WHERE (:category IS NULL OR i.category = :category) " +
                    "AND (:title IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :title, '%')))"
    )
    List<Item> findFilteredProductsWithUser(
            @Param("category") String category,
            @Param("title") String title,
            Sort sort
    );

    // 특정 ID로 Item을 찾으면서 User 정보도 Fetch Join으로 함께 로드
    @Query("SELECT i FROM Item i JOIN FETCH i.user WHERE i.id = :id")
    Optional<Item> findItemWithUserById(@Param("id") Integer id);

    // 💡 사용자가 등록한 물품 목록 조회 (판매자 ID 기준)
    @Query("SELECT i FROM Item i JOIN FETCH i.user u WHERE u.id = :userId")
    List<Item> findItemsByUserIdWithUser(@Param("userId") Integer userId);

    // 💡 사용자가 대여한 물품 목록 조회 (Renter ID 기준)
    @Query("SELECT i FROM Item i JOIN FETCH i.user u JOIN FETCH i.renter r WHERE r.id = :userId")
    List<Item> findItemsByRenterIdWithSellerAndRenter(@Param("userId") Integer userId);

    // 기타 편의 메서드들
    List<Item> findByCategory(String category);
    List<Item> findByStatus(Item.Status status);
    List<Item> findByTitleContainingOrDescriptionContaining(String titleKeyword, String descriptionKeyword);
}
