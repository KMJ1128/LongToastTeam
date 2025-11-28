package com.longtoast.bilbil_api.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    /**
     * roomId가 필요 없는 일반 알림용 (기존 호출 코드 호환용)
     */
    public void sendMessage(String targetToken, String title, String body) {
        sendMessage(targetToken, title, body, null);
    }

    /**
     * 채팅 알림 등에서 roomId까지 같이 보내는 버전
     */
    public void sendMessage(String targetToken, String title, String body, Long roomId) {
        if (targetToken == null || targetToken.isBlank()) {
            log.warn("❌ FCM 전송 실패: targetToken 이 비어있음");
            return;
        }

        try {
            Message.Builder builder = Message.builder()
                    .setToken(targetToken)
                    // 🔵 안드로이드에서 message.data["title"], ["body"], ["roomId"] 로 읽을 수 있게 data에 넣음
                    .putData("title", title)
                    .putData("body", body);

            if (roomId != null) {
                builder.putData("roomId", String.valueOf(roomId));
            }

            // notification 도 같이 세팅 (백그라운드일 때 시스템이 알림 표시)
            builder.setNotification(
                    Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
            );

            Message message = builder.build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("📨 FCM 전송 성공: {}", response);
        } catch (Exception e) {
            log.error("❌ FCM 전송 중 오류 발생", e);
        }
    }
}