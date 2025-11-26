package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.RentalDecisionRequest;
import com.longtoast.bilbil_api.dto.RentalRequestDto;
import com.longtoast.bilbil_api.service.RentalService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rental")
public class RentalController {

    private final RentalService rentalService;

    @PostMapping("/request")
    public ResponseEntity<MsgEntity> createRentalRequest(
            @RequestBody RentalRequestDto dto,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        try {
            Transaction transaction = rentalService.createRentalRequest(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("transactionId", transaction.getId());
            data.put("startDate", transaction.getStartDate());
            data.put("endDate", transaction.getEndDate());
            return ResponseEntity.ok(new MsgEntity("대여 요청이 기록되었습니다.", data));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("요청 오류", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "대여 요청 처리 중 문제가 발생했습니다."));
        }
    }

    @PostMapping("/accept")
    public ResponseEntity<MsgEntity> acceptRental(
            @RequestBody RentalDecisionRequest dto,
            @AuthenticationPrincipal Integer currentUserId
    ) {
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MsgEntity("인증 오류", "로그인이 필요합니다."));
        }

        try {
            Transaction transaction = rentalService.acceptRental(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("transactionId", transaction.getId());
            data.put("startDate", transaction.getStartDate());
            data.put("endDate", transaction.getEndDate());
            data.put("itemId", transaction.getItem().getId());
            return ResponseEntity.ok(new MsgEntity("대여가 확정되었습니다.", data));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MsgEntity("요청 오류", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MsgEntity("내부 서버 오류", "대여 확정 처리 중 문제가 발생했습니다."));
        }
    }
}
