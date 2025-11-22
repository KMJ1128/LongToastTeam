package com.longtoast.bilbil.api

import com.longtoast.bilbil.AuthTokenManager
import com.longtoast.bilbil.ServerConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 🔥 Spring Boot 서버 주소 (Wi-Fi 동일 네트워크)
    // 💡 [통합] ServerConfig에서 주소를 가져와 중앙 관리합니다.
    private const val BASE_URL = ServerConfig.HTTP_BASE_URL 

    // ------------------------------------------------------------------
    // 🔐 1. Authorization 헤더 자동 추가 Interceptor
    // ------------------------------------------------------------------
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            val token = AuthTokenManager.getToken()

            // ❗ 소셜 로그인 요청은 토큰 헤더 붙이면 안 됨
            val path = originalRequest.url.encodedPath

            if (path.contains("/kakao/login/token") ||
                path.contains("/naver/login/token")
            ) {
                Log.d("Retrofit", "소셜 로그인 요청 → Authorization 헤더 제거")
                return chain.proceed(originalRequest)
            }

            // JWT 토큰이 존재하면 Authorization 헤더 추가
            if (token != null) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token") // 기존 헤더 덮어쓰기
                    .build()

                Log.d("Retrofit", "Authorization 추가됨 → Bearer $token")
                return chain.proceed(newRequest)
            }

            Log.w("Retrofit", "JWT 토큰 없음 → 기본 요청으로 진행")
            return chain.proceed(originalRequest)
        }
    }

    // ------------------------------------------------------------------
    // 2. OkHttpClient (Interceptor 포함)
    // ------------------------------------------------------------------
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .build()
    }

    // ------------------------------------------------------------------
    // 3. Retrofit Builder
    // ------------------------------------------------------------------
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ------------------------------------------------------------------
    // 4. ApiService 인스턴스 반환
    // ------------------------------------------------------------------
    fun getApiService(): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}