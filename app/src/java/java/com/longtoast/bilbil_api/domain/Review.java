package com.longtoast.bilbil_api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 거래 정보 (transactions 테이블)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    // 🔗 리뷰 작성자 (users 테이블)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    // ⭐ 1~5 사이의 별점
    @Column(nullable = false)
    private int rating;

    // ⭐ 리뷰 내용
    @Column(columnDefinition = "TEXT")
    private String comment;

    // ⭐ 작성일 자동 저장
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
