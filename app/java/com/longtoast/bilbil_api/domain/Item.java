package com.longtoast.bilbil_api.domain;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // 판매자/대여자 (기존 필드)
    private User user;

    // 💡 변경된 필드: 물품을 대여한 사용자 (renter_id 컬럼)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = true) // 거래가 완료되지 않으면 null
    private User renter;


    private String title;
    private Integer price;

    @Column(name = "price_unit")
    private Integer price_unit;

    private String description;
    private String category;

    //  보증금
    private Integer deposit;

    private Double latitude;
    private Double longitude;

    //  거래 위치: DB의 address 컬럼에 매핑
    @Column(name = "address")
    private String tradeLocation;

    //  다중 이미지 관계 설정
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemImage> itemImages;


    @Builder.Default
    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Enum 정의
    public enum Status{
        AVAILABLE,
        RESERVED,
        RENTED,
        UNAVAILABLE
    }
}