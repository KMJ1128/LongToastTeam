package com.longtoast.bilbil_api.dto;

import com.longtoast.bilbil_api.domain.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    // 1. 판매자 정보 (Detail, ChatRoom 생성 및 List에 필요)
    private Integer sellerId;              // 채팅방 생성에 필요
    private String sellerNickname;         // 닉네임
    private int sellerCreditScore;         // 신용점수

    // 🔥 추가: 판매자 프로필 이미지 URL
    private String sellerProfileImageUrl;

    // 2. 물품 공통 정보
    private Long id;
    private String title;
    private int price;
    private int price_unit;
    private String category;

    // 3. 상세 정보
    private String description;
    private Integer deposit;           // 보증금
    private String tradeLocation;      // 거래 위치 (Item.address 컬럼)
    private String address;            // 별도 주소 필드
    private String imageUrl;           // 메인 이미지 URL (단일)
    private Double latitude;
    private Double longitude;

    // 업로드 파일 접근을 위한 이미지 URL 리스트
    private List<String> imageUrls;

    // 4. 상태 및 시간
    private Item.Status status;
    private LocalDateTime created_at;

    // ✅ 거래 PK (내가 렌트한 물품 목록에서 리뷰 버튼 표시용)
    private Long transactionId;
}
