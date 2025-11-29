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

    // ================== 기본 정보 ==================
    private String nickname; // 닉네임

    private String username;

    @Nullable
    private String password;

    @Column(nullable = true)
    private String email; // optional, 소셜 로그인시 이메일이 있으면 저장
    @Column
    private String phoneNumber;

    // ⚠ DB 컬럼: profile_image_url 과 매핑
    @Column(name = "profile_image_url", columnDefinition = "MEDIUMTEXT")
    private String profileImageUrl; // optional, 프로필 이미지

    // ⚠ DB 컬럼: credit_score 와 매핑
    @Builder.Default
    @Column(name = "credit_score")
    private Integer creditScore = 720; // 기본 신용점수

    @Nullable
    private String address;

    // ⚠ DB 컬럼: location_latitude / location_longitude 와 매핑
    @Column(name = "location_latitude")
    private Double locationLatitude;

    @Column(name = "location_longitude")
    private Double locationLongitude;

    // ⚠ DB 컬럼: created_at, deleted_at 과 매핑
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ================== 연관 관계 ==================
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<SocialLogin> socialLogins; // 여러 소셜 계정 연결 가능

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Item> items;

    // ⚠ DB 컬럼: fcm_token 과 매핑
    @Column(name = "fcm_token")
    private String fcmToken;
}
