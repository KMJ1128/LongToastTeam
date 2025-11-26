package com.longtoast.bilbil_api.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 💡 Import 추가

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// UNIQUE (item_id, lender_id, borrower_id) 제약 조건은 JPA가 테이블을 생성할 때 자동 반영됩니다.
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id; // 채팅방의 고유 ID (Primary Key)

    // 🚨 [핵심 수정] Item 객체 직렬화 방지
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // 🚨 [핵심 수정] User 객체 직렬화 방지
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false)
    private User lender;

    // 🚨 [핵심 수정] User 객체 직렬화 방지
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}