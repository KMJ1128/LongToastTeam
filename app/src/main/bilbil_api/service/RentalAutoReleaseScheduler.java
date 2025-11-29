package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import com.longtoast.bilbil_api.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalAutoReleaseScheduler {

    private final TransactionRepository transactionRepository;
    private final ItemService itemService;

    @Scheduled(cron = "0 5 0 * * *")   // 매일 새벽 00:05에 실행
    public void completeExpiredRentals() {

        LocalDate today = LocalDate.now();
        log.info("⏳ [스케줄러] 대여 기간 종료된 거래 자동 처리 시작: {}", today);

        // 1) endDate < 오늘 AND status = ACCEPTED
        List<Transaction> expired = transactionRepository
                .findByStatusAndEndDateBefore(Transaction.Status.ACCEPTED, today);

        log.info("📌 만료된 거래 개수: {}", expired.size());

        for (Transaction tx : expired) {
            Long itemId = tx.getItem().getId();

            // 2) 아이템 상태를 AVAILABLE로 변경
            itemService.setItemStatus(itemId, Item.Status.AVAILABLE);

            // 3) 거래 상태를 COMPLETED로 변경
            tx.setStatus(Transaction.Status.COMPLETED);
            transactionRepository.save(tx);

            log.info("✔ 거래 완료 처리: itemId={}, txId={}", itemId, tx.getId());
        }

        log.info("🏁 [스케줄러] 만료 거래 자동 처리 완료");
    }
}
