package com.longtoast.bilbil_api.dto;

import lombok.Data;

@Data // Lombok: Getter, Setter, toString 등을 자동 생성
public class KakaoDTO {

    private long id;
    private KakaoAccount kakao_account;

    @Data
    public static class KakaoAccount {
        // private String email;d
        private Profile profile;

        @Data
        public static class Profile {
            private String nickname;
            private String profile_image_url;
            private String thumbnail_image_url;
        }
    }

    // 편하게 nickname/email 꺼낼 수 있도록 helper 메서드 추가
    public String getNickname() {
        return kakao_account != null && kakao_account.getProfile() != null
                ? kakao_account.getProfile().getNickname()
                : null;
    }

    /*public String getEmail() {
        return kakao_account != null ? kakao_account.getEmail() : null;
    }*/
}