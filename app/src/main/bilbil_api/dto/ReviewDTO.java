package com.longtoast.bilbil_api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewDTO {

    private Long reviewId;
    private Long transactionId;
    private Long reviewerId;
    private String reviewerNickname;
    private int rating;
    private String comment;
    private String createdAt;
    private String itemTitle;       // 거래된 물품명
    private String sellerNickname;  // 판매자 닉네임
    private String rentalPeriod;    // "YYYY-MM-DD ~ YYYY-MM-DD"
}
