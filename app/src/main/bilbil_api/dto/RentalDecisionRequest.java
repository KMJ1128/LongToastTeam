package com.longtoast.bilbil_api.dto;

import lombok.Data;

@Data
public class RentalDecisionRequest {

    private Integer roomId;
    private Long itemId;
    private Integer lenderId;
    private Integer borrowerId;

    private String startDate;
    private String endDate;
    private Integer totalAmount;
}
