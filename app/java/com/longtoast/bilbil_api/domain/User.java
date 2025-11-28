package com.longtoast.bilbil_api.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 자동 증가 ID


    private String nickname; // 닉네임, 로그인에 사용

    private String username;
    @Nullable
    private String password;

    @Column(nullable = true)
    private String email; // optional, 소셜 로그인시 이메일이 있으면 저장
    @Column(nullable = true)
    private String profileImageUrl; // optional, 프로필 이미지

    @Builder.Default
    private Integer creditScore = 720; // 기본 신용점수
    //
    @Nullable
    private String address;
    private Double locationLatitude;
    private Double locationLongitude;

    private LocalDateTime createdAt;


    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<SocialLogin> socialLogins; // 여러 소셜 계정 연결 가능

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Item> items;

    @Column(name = "fcm_token")
    private String fcmToken;

}
