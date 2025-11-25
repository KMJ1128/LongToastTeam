package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.MemberDTO;
import com.longtoast.bilbil_api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository userRepository;

    /**
     * [최종] DB에서 인증된 사용자 ID를 기반으로 전체 정보를 조회합니다.
     */
    @Transactional(readOnly = true)
    public MemberDTO getMemberInfoFromDb(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // 2. User Entity의 필드를 MemberDTO로 변환하여 반환
        return new MemberDTO(
                user.getId(),
                user.getNickname(),
                user.getUsername(), // 💡 [추가] username 필드 반환
                user.getAddress(),

                // Null 체크 및 기본값 0.0 설정
                user.getLocationLatitude() != null ? user.getLocationLatitude() : 0.0,
                user.getLocationLongitude() != null ? user.getLocationLongitude() : 0.0,
                user.getCreditScore(),
                user.getProfileImageUrl(),
                user.getCreatedAt()
        );
    }

    /**
     * ✅ [핵심 추가] 프로필 정보를 업데이트하고 트랜잭션을 커밋합니다.
     */
    @Transactional // 💡 쓰기 작업이므로 @Transactional 필요
    public void updateMemberProfile(Integer userId, MemberDTO dto, MultipartFile profileImage) {
        // 🔑 [핵심 수정] DTO에 어떤 ID가 있든, JWT에서 추출된 userId만 사용하여 사용자를 조회합니다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // 닉네임, 주소, 위치 정보 업데이트
        user.setNickname(dto.getNickname());
        user.setAddress(dto.getAddress());
        user.setLocationLatitude(dto.getLocationLatitude());
        user.setLocationLongitude(dto.getLocationLongitude());

        if (profileImage != null && !profileImage.isEmpty()) {
            String storedUrl = saveProfileImage(userId, profileImage);
            user.setProfileImageUrl(storedUrl);
        }

        // username 필드는 카카오 로그인 시 설정된 값이므로, 여기서 업데이트하지 않고 유지합니다.
        // user.setUsername(dto.getUsername()); // 주석 처리 또는 제거

        userRepository.save(user);
    }

    private String saveProfileImage(Integer userId, MultipartFile profileImage) {
        try {
            Path uploadDir = Paths.get("/uploads/profile");
            Files.createDirectories(uploadDir);

            String filename = String.format("profile_%d_%d.jpg", userId, System.currentTimeMillis());
            Path filePath = uploadDir.resolve(filename);

            Files.copy(profileImage.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/profile/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("프로필 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }
}
