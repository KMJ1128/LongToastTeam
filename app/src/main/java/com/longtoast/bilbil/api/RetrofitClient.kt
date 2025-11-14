package com.longtoast.bilbil.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 🚨 중요: 여기에 Spring Boot 서버의 주소를 입력하세요!
    // 개발 중이라면 일반적으로 http://10.0.2.2:8080 (Android Emulator의 로컬호스트 주소)
    // 실제 서버라면 https://your-domain.com
    private const val BASE_URL = "http://172.16.102.73:8080/"
//김민재 로컬PC http://192.168.0.211:8080
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // JSON 자동 변환
            .build()
    }

    // ApiService 인스턴스를 얻는 함수
    fun getApiService(): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}