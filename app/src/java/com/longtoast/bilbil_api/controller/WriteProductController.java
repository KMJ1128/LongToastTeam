package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.config.JwtTokenProvider;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.ProductCreateRequest;
import com.longtoast.bilbil_api.service.WriteProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/writeproduct")
public class WriteProductController {

    private final WriteProductService writeProductService;
    private final JwtTokenProvider jwtTokenProvider;


    /**
     * POST /writeproduct/create
     * ✅ 멀티파트 업로드를 통해 상품 정보와 이미지를 함께 받습니다.
     */
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MsgEntity> createProduct(
            @RequestPart("product") ProductCreateRequest productCreateRequest,
            @RequestPart(value = "images") List<MultipartFile> images,
            Authentication authentication
    ) {
        System.out.println("\n=== [DEBUG] /writeproduct/create 요청 들어옴 ===");
        System.out.println("Authentication = " + authentication);
        // 1. 인증 객체에서 userId 추출
        Integer userId = Integer.valueOf(authentication.getName());

        try {
            int savedItemId = writeProductService.createProduct(productCreateRequest, images, userId);

            return ResponseEntity.ok()
                    .body(new MsgEntity("물품 등록 및 이미지 저장 성공", savedItemId));

        } catch (WriteProductService.UserNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(new MsgEntity("사용자 오류", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new MsgEntity("처리 오류", "데이터 처리 중 문제가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 테스트용 JWT 토큰 생성 엔드포인트
     */
    @GetMapping("/testtoken/{userId}")
    public ResponseEntity<MsgEntity> getTestToken(@PathVariable Integer userId) {
        String token = jwtTokenProvider.createToken(userId);

        return ResponseEntity.ok()
                .body(new MsgEntity("테스트 JWT 토큰 생성 성공", token));
    }

    @PutMapping("/update/{itemId}")
    public ResponseEntity<MsgEntity> updateProduct(
            @PathVariable Integer itemId,
            @RequestBody ProductCreateRequest productCreateRequest,
            Authentication authentication
    ) {
        Integer userId = Integer.valueOf(authentication.getName());

        try {
            writeProductService.updateProduct(itemId, productCreateRequest, userId);
            return ResponseEntity.ok(new MsgEntity("상품이 수정되었습니다.", itemId));
        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(new MsgEntity("수정 실패", e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{itemId}")
    public ResponseEntity<MsgEntity> deleteProduct(
            @PathVariable Integer itemId,
            Authentication authentication
    ) {
        Integer userId = Integer.valueOf(authentication.getName());

        try {
            writeProductService.deleteProduct(itemId, userId);
            return ResponseEntity.ok(new MsgEntity("삭제되었습니다.", itemId));
        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(new MsgEntity("삭제 실패", e.getMessage()));
        }
    }
}
