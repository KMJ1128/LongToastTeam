package com.longtoast.bilbil_api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "item_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    // Item 엔티티와 ManyToOne 관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "is_main")
    @Builder.Default
    private Boolean isMain = false; // 메인 이미지 여부
}