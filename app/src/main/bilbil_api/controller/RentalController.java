package com.longtoast.bilbil_api.controller;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.dto.MsgEntity;
import com.longtoast.bilbil_api.dto.RentalDecisionRequest;
import com.longtoast.bilbil_api.model.ChatMessage;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import com.longtoast.bilbil_api.service.ChatService;
import com.longtoast.bilbil_api.service.ItemService;
import com.longtoast.bilbil_api.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rental")
public class RentalController {

    private final TransactionService transactionService;
    private final ItemService itemService;
    private final ChatService chatService;
    private final TransactionRepository transactionRepository;  // ★ 추가

    @PostMapping("/approve")
    public ResponseEntity<MsgEntity> approveRental(
            @RequestBody RentalDecisionRequest req,
            @AuthenticationPrincipal Integer currentUserId
    ) {

        LocalDate startDate = LocalDate.parse(req.getStartDate());
        LocalDate endDate = LocalDate.parse(req.getEndDate());

        // 1. 거래 생성
        Transaction tx = transactionService.createTransaction(
                req.getItemId(),
                req.getLenderId(),
                req.getBorrowerId(),
                startDate,
                endDate,
                req.getTotalAmount()
        );

        // 2. 아이템 상태 업데이트
        itemService.setItemStatus(req.getItemId(), Item.Status.RENTED);

        // 3. 채팅방에 시스템 메시지 저장
        ChatMessage msg = chatService.saveChatMessage(
                req.getRoomId(),
                currentUserId,
                "대여가 확정되었습니다.\n기간: " + req.getStartDate() + " ~ " + req.getEndDate(),
                null
        );

        Map<String, Object> response = Map.of(
                "transactionId", tx.getId(),
                "startDate", startDate,
                "endDate", endDate,
                "totalAmount", tx.getTotalPrice()
        );
        chatService.broadcastMessage(req.getRoomId(), msg);
        return ResponseEntity.ok(new MsgEntity("대여 확정 완료", response));
    }

    @GetMapping("/item/{itemId}/schedules")
    public ResponseEntity<MsgEntity> getSchedules(@PathVariable Long itemId) {

        List<Transaction> list = transactionRepository
                .findByItem_Id(itemId)
                .stream()
                // ★ COMPLETED 는 제외!
                .filter(t -> t.getStatus() == Transaction.Status.ACCEPTED
                        || t.getStatus() == Transaction.Status.REQUESTED)
                .toList();

        List<Map<String, String>> schedules = list.stream()
                .map(t -> Map.of(
                        "startDate", t.getStartDate().toString(),
                        "endDate", t.getEndDate().toString()
                ))
                .toList();

        return ResponseEntity.ok(new MsgEntity("대여 일정 조회 성공", schedules));
    }

}
