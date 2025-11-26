package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r " +
            "LEFT JOIN FETCH r.transaction t " +
            "LEFT JOIN FETCH r.reviewer u " +
            "WHERE t.item.id = :itemId")
    List<Review> findReviewsByItemId(Integer itemId);

    // 거래 + 작성자 기준으로 이미 리뷰가 있는지 확인
    boolean existsByTransaction_IdAndReviewer_Id(Long transactionId, Integer reviewerId);
}
