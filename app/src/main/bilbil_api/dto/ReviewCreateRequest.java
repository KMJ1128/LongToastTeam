package com.longtoast.bilbil_api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCreateRequest {

    private Long transactionId;
    private int rating;
    private String comment;
}
