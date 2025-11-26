package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Review;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.ReviewCreateRequest;
import com.longtoast.bilbil_api.dto.ReviewDTO;
import com.longtoast.bilbil_api.repository.ReviewRepository;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * ✅ 아이템별 리뷰 조회
     */
    @Transactional(readOnly = true)
    public List<ReviewDTO> getReviewsByItemId(Integer itemId) {

        List<Review> reviews = reviewRepository.findReviewsByItemId(itemId);

        return reviews.stream()
                .filter(r -> r.getTransaction() != null && r.getReviewer() != null)
                .map(review -> {
                    Transaction transaction = review.getTransaction();
                    User reviewer = review.getReviewer();

                    Long transactionId = (transaction != null) ? transaction.getId() : null;
                    Long reviewerId = (reviewer != null && reviewer.getId() != null)
                            ? reviewer.getId().longValue()
                            : null;

                    String reviewerNickname = (reviewer != null && reviewer.getNickname() != null)
                            ? reviewer.getNickname()
                            : "알 수 없는 사용자";

                    String createdAt = (review.getCreatedAt() != null)
                            ? review.getCreatedAt().format(FORMATTER)
                            : null;

                    return ReviewDTO.builder()
                            .reviewId(review.getId())
                            .transactionId(transactionId)
                            .reviewerId(reviewerId)
                            .reviewerNickname(reviewerNickname)
                            .rating(review.getRating())
                            .comment(review.getComment())
                            .createdAt(createdAt)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * ✅ 리뷰 작성
     * @param reviewerId 현재 로그인한 사용자 ID (@AuthenticationPrincipal Integer)
     */
    @Transactional
    public ReviewDTO createReview(Integer reviewerId, ReviewCreateRequest request) {

        // 1) 작성자 조회 (Optional → orElseThrow)
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() ->
                        new IllegalArgumentException("리뷰 작성자(유저)를 찾을 수 없습니다. id=" + reviewerId)
                );

        // 2) 거래 조회
        Transaction transaction = transactionRepository.findById(request.getTransactionId())
                .orElseThrow(() ->
                        new IllegalArgumentException("거래를 찾을 수 없습니다. id=" + request.getTransactionId())
                );

        // 3) 이 거래의 대여자인지 검증 (borrower만 리뷰 허용)
        if (transaction.getBorrower() == null ||
                !transaction.getBorrower().getId().equals(reviewerId)) {
            throw new IllegalStateException("이 거래의 대여자만 리뷰를 작성할 수 있습니다.");
        }

        // 4) 이미 리뷰를 작성했는지 중복 체크
        boolean alreadyExists =
                reviewRepository.existsByTransaction_IdAndReviewer_Id(
                        transaction.getId(),   // Long
                        reviewerId             // Integer
                );

        if (alreadyExists) {
            throw new IllegalStateException("이미 이 거래에 대한 리뷰를 작성했습니다.");
        }

        // 5) Review 엔티티 생성 & 저장
        Review review = Review.builder()
                .transaction(transaction)
                .reviewer(reviewer)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);

        // 6) DTO로 변환해서 반환
        return ReviewDTO.builder()
                .reviewId(saved.getId())
                .transactionId(transaction.getId())
                .reviewerId(reviewer.getId().longValue())
                .reviewerNickname(reviewer.getNickname())
                .rating(saved.getRating())
                .comment(saved.getComment())
                .createdAt(
                        saved.getCreatedAt() != null
                                ? saved.getCreatedAt().format(FORMATTER)
                                : null
                )
                .build();
    }
}
