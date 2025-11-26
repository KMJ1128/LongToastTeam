package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.LocationRequest;
import com.longtoast.bilbil_api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final UserRepository userRepository;

    @Transactional
    public void updateLocation(Integer userId, LocationRequest req) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found ID: " + userId));

        user.setLocationLatitude(req.getLatitude());
        user.setLocationLongitude(req.getLongitude());

        // 🚨 [핵심 수정] Address 필드를 LocationRequest에서 가져와 User 엔티티에 설정
        user.setAddress(req.getAddress());

        userRepository.save(user);
    }
}
