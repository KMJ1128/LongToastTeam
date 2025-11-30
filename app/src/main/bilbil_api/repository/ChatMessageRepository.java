package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.model.ChatMessage;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoom_IdOrderBySentAtAsc(Integer chatRoomId);

    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.CACHEABLE, value = "false")
    })
    Optional<ChatMessage> findTopByChatRoom_IdOrderBySentAtDesc(Integer chatRoomId);

    @Query("SELECT m FROM ChatMessage m " +
            "WHERE m.chatRoom.id = :roomId " +
            "AND m.sender.id <> :currentUserId " +
            "AND m.isRead = false")
    List<ChatMessage> findUnreadMessages(@Param("roomId") Integer roomId,
                                         @Param("currentUserId") Integer currentUserId);


    // ⭐ unread count 추가
    @Query("SELECT COUNT(m) FROM ChatMessage m " +
            "WHERE m.chatRoom.id = :roomId " +
            "AND m.sender.id <> :currentUserId " +
            "AND m.isRead = false")
    int countUnreadMessages(@Param("roomId") Integer roomId,
                            @Param("currentUserId") Integer currentUserId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true " +
            "WHERE m.chatRoom.id = :roomId " +
            "AND m.sender.id <> :currentUserId " +
            "AND m.isRead = false")
    void markMessagesAsRead(@Param("roomId") Integer roomId,
                            @Param("currentUserId") Integer currentUserId);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId AND m.sender.id <> :currentUserId AND m.isRead = true")
    List<ChatMessage> findUnreadMessagesByRoomAndReceiver(@Param("roomId") Integer roomId,
                                                          @Param("currentUserId") Integer currentUserId);




}
