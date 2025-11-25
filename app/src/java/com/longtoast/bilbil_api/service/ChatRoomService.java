// com.longtoast.bilbil_api.service.ChatRoomService.java
package com.longtoast.bilbil_api.service;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.User;
import com.longtoast.bilbil_api.repository.ChatRoomRepository;
import com.longtoast.bilbil_api.repository.ProductsRepository;
import com.longtoast.bilbil_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional // DB 변경(생성)이 필요하므로 Transactional 사용
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ProductsRepository productsRepository; // Item 엔티티 조회를 위해 ProductsRepository가 주입된다고 가정

    /**
     * 특정 아이템에 대한 구매자-판매자 간의 채팅방을 찾거나 새로 생성합니다.
     */
    public ChatRoom findOrCreateRoom(Integer itemId, Integer lenderId, Integer borrowerId) {

        Item item = productsRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Item not found with ID: " + itemId));

        User lender = userRepository.findById(lenderId)
                .orElseThrow(() -> new EntityNotFoundException("Lender not found with ID: " + lenderId));

        User borrower = userRepository.findById(borrowerId)
                .orElseThrow(() -> new EntityNotFoundException("Borrower not found with ID: " + borrowerId));

        return chatRoomRepository.findByItemAndLenderAndBorrower(item, lender, borrower)
                .orElseGet(() -> {
                    ChatRoom newRoom = ChatRoom.builder()
                            .item(item)
                            .lender(lender)
                            .borrower(borrower)
                            .build();

                    return chatRoomRepository.save(newRoom);
                });
    }
}