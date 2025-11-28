package com.longtoast.bilbil_api.dto;

public class FcmTokenRequest {

    private String token;

    public FcmTokenRequest() {}

    public FcmTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}