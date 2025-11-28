package com.longtoast.bilbil_api.model;

import com.longtoast.bilbil_api.domain.ChatRoom;
import com.longtoast.bilbil_api.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@Entity
@Table(name = "chats")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", columnDefinition = "MEDIUMTEXT", nullable = true)
    private String imageUrl;

    @Column(name = "sent_at")
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "is_read", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isRead = false;


    /**
     * senderId JSON 출력
     */
    @JsonProperty("senderId")
    public Integer getSenderId() {
        return this.sender != null ? this.sender.getId() : null;
    }

    /**
     * 🚀 roomId JSON 출력 (민재가 지금 딱 필요했던 부분)
     */
    @JsonProperty("roomId")
    public Integer getRoomId() {
        return this.chatRoom != null ? this.chatRoom.getId() : null;
    }
}
