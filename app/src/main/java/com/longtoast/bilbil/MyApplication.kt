package com.longtoast.bilbil

import android.app.Application
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

class MyApplication : Application() {

    private val KAKAO_NATIVE_APP_KEY = "7a3a72c388ba6dfc6df8ca9715f284ff"

    private val NAVER_CLIENT_ID = "a7CXZxOYZfr0Oz_swkzL"
    private val NAVER_CLIENT_SECRET = "yVqKNPr2R8"
    private val NAVER_CLIENT_NAME = "BilBil"

    override fun onCreate() {
        super.onCreate()

        // 1. 카카오 로그인 SDK
        KakaoSdk.init(this, KAKAO_NATIVE_APP_KEY)

        // ❌ 2. 카카오 지도 SDK 제거 — 강제종료의 원인
        // KakaoMapSdk.init(this, KAKAO_NATIVE_APP_KEY)

        // 3. 네이버 로그인 SDK (필수)
        NaverIdLoginSDK.initialize(
            this,
            NAVER_CLIENT_ID,
            NAVER_CLIENT_SECRET,
            NAVER_CLIENT_NAME
        )

        // 4. JWT 관리
        AuthTokenManager.init(this)

        // 5. 채팅 알림 워커
        scheduleChatRefreshWorker()

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("FCM", "🔴 FCM 토큰 가져오기 실패", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("FCM", "✅ 앱 시작 시 FCM 토큰: $token")
            }
    }

    private fun scheduleChatRefreshWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ChatRefreshWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "chat_refresh_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
