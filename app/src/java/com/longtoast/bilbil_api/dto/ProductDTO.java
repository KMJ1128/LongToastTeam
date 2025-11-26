package com.longtoast.bilbil_api.dto;


import com.longtoast.bilbil_api.domain.Item;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.util.List; // 💡 [추가] List Import

@Data
@Builder // ✅ 이 어노테이션이 builder() 메서드를 생성합니다.
@NoArgsConstructor // ✅ 빌더 패턴 사용 시 필수 (기본 생성자)
@AllArgsConstructor
public class ProductDTO {

    // 1. 판매자 정보 (Detail, ChatRoom 생성 및 List에 필요)
    private Integer sellerId;          //  채팅방 생성에 필요
    private String sellerNickname;     //  닉네임 필드명 통일
    private int sellerCreditScore;     //  신용점수 필드명 통일

    // 2. 물품 공통 정보
    private Long id;
    private String title;
    private int price;
    private String category;

    // 3. 상세 정보 (List에서는 사용되지만 Detail View에서 필수)
    private String description;
    private Integer deposit;           //  보증금
    private String tradeLocation;      //  거래 위치
    private String address;
    private String imageUrl;           //  메인 이미지 URL (단일)

    // 업로드 파일 접근을 위한 이미지 URL 리스트
    private List<String> imageUrls;

    // 4. 상태 및 시간
    private Item.Status status;
    private LocalDateTime created_at;
    // ✅ 거래 PK
    private Long transactionId;

    // ✅ 확정된 대여 기간 (yyyy-MM-dd~yyyy-MM-dd)
    private List<String> reservedPeriods;
}