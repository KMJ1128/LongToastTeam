// com.longtoast.bilbil_api.dto.MemberDTO.java
package com.longtoast.bilbil_api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {
    private Integer id;
    private String nickname;
    private String username; // 💡 [핵심 추가] username 필드 추가
    private String address;
    private String phoneNumber;
    private Double locationLatitude;
    private Double locationLongitude;

    private int creditScore;
    private String profileImageUrl;

    private LocalDateTime createdAt; // 총 9개 필드가 되었습니다. (ID, Nickname, Username, Address, Lat, Lon, Credit, ImageUrl, CreatedAt)
}