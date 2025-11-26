package com.longtoast.bilbil_api.dto;

import lombok.Data;

@Data
public class LocationRequest {
    private Double latitude;
    private Double longitude;
    private String address;
}
