// com.longtoast.bilbil_api.service.ReviewService.java
package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
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

    // =========================================================
    // ✅ 아이템별 리뷰 조회
    // =========================================================
    @Transactional(readOnly = true)
    public List<ReviewDTO> getReviewsByItemId(Integer itemId) {
        List<Review> reviews = reviewRepository.findReviewsByItemId(itemId);

        return reviews.stream()
                .filter(r -> r.getTransaction() != null && r.getReviewer() != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================================================
    // ✅ 내가 쓴 리뷰 전체 조회
    // =========================================================
    @Transactional(readOnly = true)
    public List<ReviewDTO> getMyReviews(Integer reviewerId) {
        List<Review> reviews = reviewRepository.findReviewsByReviewerId(reviewerId);

        return reviews.stream()
                .filter(r -> r.getTransaction() != null && r.getReviewer() != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================================================
    // ✅ 내가 받은 리뷰 전체 조회 (판매자 기준)
    // =========================================================
    @Transactional(readOnly = true)
    public List<ReviewDTO> getReceivedReviews(Integer sellerId) {
        List<Review> reviews = reviewRepository.findReviewsReceivedBySellerId(sellerId);

        return reviews.stream()
                .filter(r -> r.getTransaction() != null && r.getReviewer() != null)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================================================
    // ✅ 리뷰 작성
    // =========================================================
    @Transactional
    public ReviewDTO createReview(Integer reviewerId, ReviewCreateRequest request) {

        // 1) 작성자 조회
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
                        transaction.getId(),
                        reviewerId
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

        // 6) ⭐ 이 리뷰의 별점으로 판매자 신용점수 갱신
        updateSellerCreditScore(transaction, request.getRating());

        // 7) DTO 변환 후 반환
        return toDTO(saved);
    }

    // =========================================================
    // 🔒 공통 DTO 변환 메서드
    // =========================================================
    private ReviewDTO toDTO(Review review) {
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

        // 물품 / 판매자 / 대여기간 정보
        String itemTitle = null;
        String sellerNickname = null;
        String rentalPeriod = null;

        if (transaction != null) {
            Item item = transaction.getItem();
            if (item != null) {
                itemTitle = item.getTitle();

                User seller = item.getUser();
                if (seller != null && seller.getNickname() != null) {
                    sellerNickname = seller.getNickname();
                }
            }

            if (transaction.getStartDate() != null && transaction.getEndDate() != null) {
                rentalPeriod = transaction.getStartDate().toString()
                        + " ~ "
                        + transaction.getEndDate().toString();
            }
        }

        return ReviewDTO.builder()
                .reviewId(review.getId())
                .transactionId(transactionId)
                .reviewerId(reviewerId)
                .reviewerNickname(reviewerNickname)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(createdAt)

                .itemTitle(itemTitle)
                .sellerNickname(sellerNickname)
                .rentalPeriod(rentalPeriod)
                .build();
    }

    // =========================================================
    // ⭐ 리뷰 1개가 들어올 때마다 판매자 신용점수 갱신 (상한/하한 없음)
    //
    //  - 기준점: 3점 → 변화 0
    //  - 2점 / 4점 → 각 -5 / +5
    //  - 1점 / 5점 → -20 / +20 (배점 2배)
    //
    //  - 한 리뷰당 현재 점수에서 delta만큼 더하거나 빼기만 함
    //  - creditScore가 null이면 720에서 시작
    // =========================================================
    private void updateSellerCreditScore(Transaction transaction, int rating) {
        if (transaction == null || transaction.getItem() == null) return;

        Item item = transaction.getItem();
        User seller = item.getUser();
        if (seller == null) return;

        // 현재 신용점수 (없으면 720 기본값)
        int currentScore = (seller.getCreditScore() != null) ? seller.getCreditScore() : 720;

        int delta;
        switch (rating) {
            case 1:
                // 3점 기준 -2점 차이 → 원래 -10점인데, x2배로 -20점
                delta = -20;
                break;
            case 2:
                // 3점 기준 -1점 차이 → -5점
                delta = -5;
                break;
            case 3:
                // 기준점 → 변화 없음
                delta = 0;
                break;
            case 4:
                // 3점 기준 +1점 차이 → +5점
                delta = 5;
                break;
            case 5:
                // 3점 기준 +2점 차이 → 원래 +10점인데, x2배로 +20점
                delta = 20;
                break;
            default:
                // 혹시 1~5가 아니면 변화 없음
                delta = 0;
        }

        int newScore = currentScore + delta;

        // ❌ 더 이상 최소 / 최대 상한치 적용하지 않음
        // 그대로 누적
        seller.setCreditScore(newScore);
        userRepository.save(seller);
    }
}
