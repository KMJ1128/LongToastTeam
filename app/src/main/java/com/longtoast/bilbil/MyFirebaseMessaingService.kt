package com.longtoast.bilbil

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID = "default_channel"
    }

    /** 새 토큰이 발급될 때마다 호출됨 */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔥 새 FCM 토큰: $token")

        // 헬퍼를 통해 서버로 업로드 (로그인 안 되어 있으면 내부에서 알아서 생략)
        FcmTokenManager.uploadTokenToServer(token)
    }

    /** 실제 푸시 메시지를 받았을 때 호출됨 */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FCM", "📨 메시지 수신: data=${message.data}, notification=${message.notification}")

        val title = message.data["title"]
            ?: message.notification?.title
            ?: "새 메시지"

        val body = message.data["body"]
            ?: message.notification?.body
            ?: ""

        val roomId = message.data["roomId"]   // ← 지금 4 찍히는 그 값

        Log.d("FCM", "roomId from FCM data = $roomId")

        showNotification(title, body, roomId)
    }

    /**
     * 안드로이드 알림 생성
     */
    private fun showNotification(title: String, message: String, roomId: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "기본 알림 채널",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val targetRoomId = roomId?.toIntOrNull()
        Log.d("FCM", "알림 Intent에 넣을 roomId = $targetRoomId")

        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            if (targetRoomId != null) {
                putExtra("ROOM_ID", targetRoomId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.bilbil)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
