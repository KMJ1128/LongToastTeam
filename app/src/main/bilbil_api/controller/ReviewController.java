// com.longtoast.bilbil_api.controller.ReviewController.java
package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.ReviewCreateRequest;
import com.longtoast.bilbil_api.dto.ReviewDTO;
import com.longtoast.bilbil_api.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * ✅ 특정 아이템에 대한 리뷰 조회
     *  - GET /reviews/item/{itemId}
     */
    @GetMapping("/item/{itemId}")
    public ResponseEntity<MsgEntity> getItemReviews(@PathVariable Integer itemId) {
        return ResponseEntity.ok(
                new MsgEntity(
                        "리뷰 조회 성공",
                        reviewService.getReviewsByItemId(itemId)
                )
        );
    }

    /**
     * ✅ 내가 쓴 리뷰 전체 조회
     *  - GET /reviews/my
     */
    @GetMapping("/my")
    public ResponseEntity<MsgEntity> getMyReviews(
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(401)
                    .body(new MsgEntity("인증 필요", "로그인이 필요합니다."));
        }

        return ResponseEntity.ok(
                new MsgEntity(
                        "내가 작성한 리뷰 목록 조회 성공",
                        reviewService.getMyReviews(currentUserId)
                )
        );
    }

    /**
     * ✅ 내가 받은 리뷰 전체 조회 (로그인 유저 기준)
     *  - GET /reviews/received
     */
    @GetMapping("/received")
    public ResponseEntity<MsgEntity> getReceivedReviews(
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(401)
                    .body(new MsgEntity("인증 필요", "로그인이 필요합니다."));
        }

        return ResponseEntity.ok(
                new MsgEntity(
                        "내가 받은 리뷰 목록 조회 성공",
                        reviewService.getReceivedReviews(currentUserId)
                )
        );
    }

    /**
     * ✅ 특정 판매자가 받은 리뷰 전체 조회 (아무나 볼 수 있음)
     *  - GET /reviews/seller/{sellerId}
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<MsgEntity> getSellerReviews(
            @PathVariable Integer sellerId
    ) {
        return ResponseEntity.ok(
                new MsgEntity(
                        "판매자가 받은 리뷰 목록 조회 성공",
                        reviewService.getReceivedReviews(sellerId)
                )
        );
    }

    /**
     * ✅ 리뷰 작성: POST /reviews
     */
    @PostMapping
    public ResponseEntity<MsgEntity> createReview(
            @AuthenticationPrincipal Integer currentUserId,
            @RequestBody ReviewCreateRequest request
    ) {
        // 1. 로그인 안 되어 있으면 401 유지
        if (currentUserId == null) {
            return ResponseEntity.status(401)
                    .body(new MsgEntity("인증 필요", "로그인이 필요합니다."));
        }

        try {
            // 2. 정상 생성
            ReviewDTO created = reviewService.createReview(currentUserId, request);

            return ResponseEntity.ok(
                    new MsgEntity(
                            "고객님의 소중한 리뷰가 등록되었습니다.",
                            created
                    )
            );
        } catch (IllegalStateException e) {

            // ⚠️ "이미 이 거래에 대한 리뷰를 작성했습니다." 인 경우만 따로 처리
            if ("이미 이 거래에 대한 리뷰를 작성했습니다.".equals(e.getMessage())) {
                return ResponseEntity
                        .badRequest()  // 400
                        .body(new MsgEntity("한 거래 당 리뷰는 1개씩 등록 가능합니다.", null));
            }

            // 그 외 IllegalStateException은 기존 로직대로 터뜨리거나, 공통 처리로 넘김
            throw e;
        }
    }
}
