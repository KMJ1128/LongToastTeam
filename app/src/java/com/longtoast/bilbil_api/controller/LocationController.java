package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.dto.LocationRequest;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;
    private static final Logger log = LoggerFactory.getLogger(LocationController.class);
    @PostMapping("/update")
    public ResponseEntity<MsgEntity> updateLocation(
            @AuthenticationPrincipal Integer currentUserId,
            @RequestBody LocationRequest request
    ) {
        log.info("[LOC] 요청 수신 userId={} lat={} lng={} address={}",
                currentUserId, request.getLatitude(), request.getLongitude() ,request.getAddress());

        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        locationService.updateLocation(currentUserId, request);

        return ResponseEntity.ok(new MsgEntity("위치 업데이트 성공", null));
    }
}
