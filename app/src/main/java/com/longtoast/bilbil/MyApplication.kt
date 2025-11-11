package com.longtoast.bilbil

import android.app.Application
import android.util.Log // 🚨 Log 임포트 추가
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class MyApplication : Application() {

    private val NATIVE_APP_KEY = "7a3a72c388ba6dfc6df8ca9715f284ff"

    override fun onCreate() {
        super.onCreate()

        // 1. 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, NATIVE_APP_KEY)

        // 2. 카카오 지도 SDK 초기화
        KakaoMapSdk.init(this, NATIVE_APP_KEY)

        // 🚨 3. [수정됨] AuthTokenManager 초기화
        AuthTokenManager.init(this)
    }
}