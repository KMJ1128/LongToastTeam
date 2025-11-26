package com.longtoast.bilbil_api.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class NaverDTO {

    private String resultcode;
    private String message;
    private Response response;

    @Getter
    @ToString
    public static class Response {
        private String id;

        // 네이버 필드: "name"
        private String name;

        // 네이버 필드: "nickname"
        private String nickname;
    }
}
