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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        // ⚠️ 네가 쓰는 서버 주소로 맞게 바꿔줘 (에뮬레이터면 보통 10.0.2.2)
        private const val BASE_URL = "http://172.16.105.93:8080/"
        private const val CHANNEL_ID = "default_channel"
    }

    private val client by lazy { OkHttpClient() }

    /** 새 토큰이 발급될 때마다 호출됨 */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔥 새 FCM 토큰: $token")

        uploadTokenToServer(token)
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

        val roomId = message.data["roomId"] // 서버에서 넣어주면 채팅방으로 이동 가능

        showNotification(title, body, roomId)
    }

    /**
     * FCM 토큰을 우리 서버 /fcm/token 으로 전송
     * 서버는 Authorization 헤더의 JWT로 유저를 식별함 (@AuthenticationPrincipal Integer userId)
     */
    private fun uploadTokenToServer(token: String) {
        val jwt = AuthTokenManager.getToken()
        val userId = AuthTokenManager.getUserId()

        if (jwt.isNullOrEmpty() || userId == null) {
            Log.d("FCM", "로그인 정보가 없어서 토큰 전송 생략 (jwt or userId null)")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = """{"token":"$token"}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/fcm/token")
                    .addHeader("Authorization", "Bearer $jwt")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { res ->
                    Log.d(
                        "FCM",
                        "토큰 업로드 결과: code=${res.code}, body=${res.body?.string()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("FCM", "FCM 토큰 서버 전송 중 오류", e)
            }
        }
    }

    /**
     * 안드로이드 알림 생성
     */
    private fun showNotification(title: String, message: String, roomId: String?) {
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8 이상 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "기본 알림 채널",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        // 알림 눌렀을 때 이동할 화면 (채팅방)
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (roomId != null) {
                putExtra("ROOM_ID", roomId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.bilbil) // 앱 아이콘 or 별도 알림 아이콘
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
