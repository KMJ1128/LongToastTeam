package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.Item;
import com.longtoast.bilbil_api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Integer> {

    Optional<ChatRoom> findByItemAndLenderAndBorrower(Item item, User lender, User borrower);

    // 🚨 기존 메서드는 제거하거나 사용하지 않습니다.
    // List<ChatRoom> findByLenderOrBorrowerOrderByCreatedAtDesc(User lender, User borrower);

    /**
     * ✅ [수정/추가] 특정 사용자가 참여한 모든 채팅방을 Fetch Join으로 조회 (N+1 문제 해결)
     * Item, Lender, Borrower 정보를 한 번에 로드합니다.
     */
    @Query("SELECT r FROM ChatRoom r " +
            "JOIN FETCH r.item i " +
            "JOIN FETCH r.lender l " +
            "JOIN FETCH r.borrower b " +
            "WHERE r.lender = :user OR r.borrower = :user " +
            "ORDER BY r.createdAt DESC")
    List<ChatRoom> findChatRoomsWithDetailsByUser(@Param("user") User user);
}