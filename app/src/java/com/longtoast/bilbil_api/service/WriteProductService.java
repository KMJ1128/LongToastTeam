package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.ItemImage;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.ProductCreateRequest;
import com.longtoast.bilbil_api.repository.ItemImageRepository;
import com.longtoast.bilbil_api.repository.ProductsRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WriteProductService {

    private final UserRepository userRepository;
    private final ProductsRepository productsRepository;
    // S3Service 제거 유지
    private final ItemImageRepository itemImageRepository;


    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * 🚨 [핵심 수정] ProductCreateRequest와 이미지 파일을 받아 로컬 디스크에 저장
     */
    public int createProduct(ProductCreateRequest dto, List<MultipartFile> images, int userId) {

        // 1. 사용자 ID로 User 엔티티를 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("존재하지 않는 사용자입니다."));

        // 2. DTO와 User 객체를 사용하여 Item 엔티티 생성
        Item item = Item.builder()
                .user(user)
                .title(dto.getTitle())
                .price(dto.getPrice())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .deposit(dto.getDeposit())
                .tradeLocation(dto.getAddress())
                .status(dto.getStatus() != null ? dto.getStatus() : Item.Status.AVAILABLE)
                .build();

        // 3. Item 엔티티 저장
        Item savedItem = productsRepository.save(item);

        // 4. 업로드된 이미지 파일을 로컬 디스크에 저장하고 URL을 DB에 기록
        saveItemImages(images, savedItem);

        return savedItem.getId().intValue();
    }

    private void saveItemImages(List<MultipartFile> images, Item savedItem) {
        if (images == null || images.isEmpty()) {
            return;
        }

        Path uploadDir = Paths.get("/uploads/product/" + savedItem.getId());
        try {
            Files.createDirectories(uploadDir);
            long baseTime = System.currentTimeMillis();

            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                if (image == null || image.isEmpty()) {
                    continue;
                }

                String filename = String.format("product_%d_%d.jpg", savedItem.getId(), baseTime + i);
                Path filePath = uploadDir.resolve(filename);
                Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                ItemImage itemImage = ItemImage.builder()
                        .item(savedItem)
                        .imageUrl(String.format("/uploads/product/%d/%s", savedItem.getId(), filename))
                        .isMain(i == 0)
                        .build();

                itemImageRepository.save(itemImage);
            }
        } catch (IOException e) {
            throw new RuntimeException("상품 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }
}
