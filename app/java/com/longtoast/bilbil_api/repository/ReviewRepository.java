package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // ✅ 특정 아이템에 달린 리뷰 조회
    @Query("SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.transaction t " +
            "LEFT JOIN FETCH r.reviewer u " +
            "WHERE t.item.id = :itemId")
    List<Review> findReviewsByItemId(@Param("itemId") Integer itemId);

    // ✅ 특정 거래 + 작성자 기준으로 이미 리뷰가 있는지 확인
    boolean existsByTransaction_IdAndReviewer_Id(Long transactionId, Integer reviewerId);

    // ✅ 내가 '쓴' 리뷰들 (reviewer 기준)
    @Query("SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.transaction t " +
            "LEFT JOIN FETCH r.reviewer u " +
            "WHERE u.id = :reviewerId")
    List<Review> findReviewsByReviewerId(@Param("reviewerId") Integer reviewerId);

    // ✅ 내가 '받은' 리뷰들 (판매자 = item.user 기준)
    @Query("SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.transaction t " +
            "LEFT JOIN FETCH t.item i " +
            "LEFT JOIN FETCH i.user seller " +
            "LEFT JOIN FETCH r.reviewer reviewer " +
            "WHERE seller.id = :sellerId")
    List<Review> findReviewsReceivedBySellerId(@Param("sellerId") Integer sellerId);
}
