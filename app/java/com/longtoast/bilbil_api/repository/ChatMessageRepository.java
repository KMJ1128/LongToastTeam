package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.model.ChatMessage;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 💡 [수정] findByChatRoom_Id로 변경
    List<ChatMessage> findByChatRoom_IdOrderBySentAtAsc(Integer chatRoomId);

    // 💡 [수정] findTopByChatRoom_Id로 변경
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "false"),
            @QueryHint(name = "jakarta.persistence.query.timeout", value = "1000") // 쿼리 시간 제한 설정 (선택 사항)
    })
    Optional<ChatMessage> findTopByChatRoom_IdOrderBySentAtDesc(Integer chatRoomId);

    List<ChatMessage> findByChatRoom_IdAndSender_IdNotAndIsReadFalse(Integer chatRoomId, Integer senderId);
}