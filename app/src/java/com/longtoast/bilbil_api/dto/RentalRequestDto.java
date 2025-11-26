package com.longtoast.bilbil_api.dto;

import lombok.Data;

@Data
public class RentalRequestDto {
    private Integer itemId;
    private Integer lenderId;
    private Integer borrowerId;
    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd
    private Integer rentFee;
    private Integer deposit;
    private Integer totalAmount;
    private String deliveryMethod;
}
