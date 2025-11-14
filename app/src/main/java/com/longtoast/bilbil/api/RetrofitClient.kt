package com.longtoast.bilbil.api

import com.longtoast.bilbil.AuthTokenManager // 🚨 AuthTokenManager 임포트
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import android.util.Log // 🚨 Log 임포트 추가
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 🚨 중요: 여기에 Spring Boot 서버의 주소를 입력하세요!
    private const val BASE_URL = "http://172.16.102.146:8080/"

    //김민재 로컬PC http://192.168.0.211:8080
    // "http://172.16.102.73:8080/"
    //"http://172.16.114.31:8080/"

    // 🚨 1. [추가됨] Authorization 헤더를 자동으로 추가하는 Interceptor
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // 저장된 토큰 가져오기
            val token = AuthTokenManager.getToken()

            // 로그인 요청에는 헤더를 추가하지 않음 (토큰이 아직 없으므로)
            if (originalRequest.url.encodedPath.contains("/kakao/login/token")) {
                return chain.proceed(originalRequest)
            }

            // 토큰이 있는 경우
            if (token != null) {
                Log.d("RetrofitClient", "Authorization 헤더에 토큰 추가: Bearer $token")
                val newRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $token") // "Bearer " 접두사 사용
                    .build()
                return chain.proceed(newRequest)
            }

            // 토큰이 없는 경우 (로그인 안 됨)
            Log.w("RetrofitClient", "토큰이 없어 Authorization 헤더 없이 요청")
            return chain.proceed(originalRequest)
        }
    }

    // 🚨 2. [추가됨] AuthInterceptor를 포함하는 OkHttpClient 생성
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor()) // 위에서 만든 인터셉터 추가
            .build()
    }

    // 🚨 3. [수정됨] Retrofit 빌더가 OkHttpClient를 사용하도록 변경
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // ⬅️ Interceptor가 포함된 Client 설정
            .addConverterFactory(GsonConverterFactory.create()) // JSON 자동 변환
            .build()
    }

    // ApiService 인턴스를 얻는 함수
    fun getApiService(): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}