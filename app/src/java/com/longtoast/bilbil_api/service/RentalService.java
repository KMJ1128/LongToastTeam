package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.dto.RentalDecisionRequest;
import com.longtoast.bilbil_api.dto.RentalRequestDto;
import com.longtoast.bilbil_api.repository.ProductsRepository;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RentalService {

    private final TransactionRepository transactionRepository;
    private final ProductsRepository productsRepository;
    private final UserRepository userRepository;

    public Transaction createRentalRequest(RentalRequestDto dto) {
        Item item = productsRepository.findById(dto.getItemId().longValue())
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));

        User lender = userRepository.findById(dto.getLenderId())
                .orElseThrow(() -> new EntityNotFoundException("대여자를 찾을 수 없습니다."));

        User borrower = userRepository.findById(dto.getBorrowerId())
                .orElseThrow(() -> new EntityNotFoundException("빌리는 사용자를 찾을 수 없습니다."));

        Transaction transaction = Transaction.builder()
                .item(item)
                .lender(lender)
                .borrower(borrower)
                .startDate(LocalDate.parse(dto.getStartDate()))
                .endDate(LocalDate.parse(dto.getEndDate()))
                .totalPrice(dto.getTotalAmount())
                .status(Transaction.Status.REQUESTED)
                .build();

        return transactionRepository.save(transaction);
    }

    public Transaction acceptRental(RentalDecisionRequest dto) {
        Transaction transaction = transactionRepository.findById(dto.getTransactionId())
                .orElseThrow(() -> new EntityNotFoundException("거래를 찾을 수 없습니다."));

        transaction.setStatus(Transaction.Status.ACCEPTED);

        Item item = transaction.getItem();
        item.setRenter(transaction.getBorrower());
        item.setStatus(Item.Status.RENTED);
        productsRepository.save(item);

        return transactionRepository.save(transaction);
    }

    public List<Transaction> getAcceptedTransactions(Long itemId) {
        return transactionRepository.findByItem_IdAndStatusIn(
                itemId,
                List.of(Transaction.Status.ACCEPTED)
        );
    }
}
