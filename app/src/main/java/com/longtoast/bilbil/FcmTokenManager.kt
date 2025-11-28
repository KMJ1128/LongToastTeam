package com.longtoast.bilbil

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * FCM 토큰을 서버에 업로드하는 공통 헬퍼
 *
 * - MyFirebaseMessagingService.onNewToken(...) 에서 사용
 * - "로그인 성공 직후"에도 사용할 수 있게 uploadCurrentToken() 제공
 */
object FcmTokenManager {

    private const val TAG = "FCM"
    private val client by lazy { OkHttpClient() }

    // 뒤에 "/" 안 붙인 버전 (URL 만들 때 // 안 생기게)
    private const val BASE_URL = ServerConfig.HTTP_BASE_URL;

    /**
     * 이미 가지고 있는 토큰을 서버로 업로드
     * - 로그인 상태가 아니면 업로드하지 않음
     */
    fun uploadTokenToServer(token: String) {
        val jwt = AuthTokenManager.getToken()
        val userId = AuthTokenManager.getUserId()

        if (jwt.isNullOrEmpty() || userId == null) {
            Log.d(TAG, "로그인 정보가 없어서 토큰 전송 생략 (jwt or userId null)")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = """{"token":"$token"}"""
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("${BASE_URL}fcm/token")
                    .addHeader("Authorization", "Bearer $jwt")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { res ->
                    Log.d(
                        TAG,
                        "토큰 업로드 결과: code=${res.code}, body=${res.body?.string()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM 토큰 서버 전송 중 오류", e)
            }
        }
    }

    /**
     * "로그인 성공 시점"에서 호출할 함수
     *
     * - 현재 디바이스의 FCM 토큰을 다시 가져와서
     *   로그인된 유저 기준으로 서버에 업로드한다.
     */
    fun uploadCurrentToken() {
        val jwt = AuthTokenManager.getToken()
        val userId = AuthTokenManager.getUserId()

        if (jwt.isNullOrEmpty() || userId == null) {
            Log.d(TAG, "로그인 정보 없음 → 현재 토큰 업로드 생략 (jwt or userId null)")
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "로그인 이후 FCM 토큰: $token")
                uploadTokenToServer(token)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "현재 FCM 토큰 가져오기 실패", e)
            }
    }
}
