package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.Transaction;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.repository.ItemRepository;
import com.longtoast.bilbil_api.repository.TransactionRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public Transaction createTransaction(
            Long itemId,
            Integer lenderId,
            Integer borrowerId,
            LocalDate start,
            LocalDate end,
            Integer totalPrice
    ) {
        Transaction tx = new Transaction();
        tx.setItem(itemRepository.findById(itemId).orElseThrow());
        tx.setLender(userRepository.findById(lenderId).orElseThrow());
        tx.setBorrower(userRepository.findById(borrowerId).orElseThrow());
        tx.setStartDate(start);
        tx.setEndDate(end);
        tx.setTotalPrice(totalPrice);
        tx.setStatus(Transaction.Status.ACCEPTED);

        Transaction saved = transactionRepository.save(tx);

        itemRepository.findById(itemId).ifPresent(item -> {
            User borrower = userRepository.findById(borrowerId).orElseThrow();
            item.setRenter(borrower);        // ★ 여기가 핵심
            item.setStatus(Item.Status.RENTED);
            itemRepository.save(item);
        });

        return saved;
    }
}
