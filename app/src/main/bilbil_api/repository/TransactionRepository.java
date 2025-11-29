package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ✅ 내가 빌린(itemIds, borrowerId) 기준으로 모든 거래 조회
    List<Transaction> findByItem_IdInAndBorrower_Id(
            List<Long> itemIds,
            Integer borrowerId
    );

    List<Transaction> findByItem_Id(Long itemId);

    List<Transaction> findByStatusAndEndDateBefore(Transaction.Status status, LocalDate date);


}