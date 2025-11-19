package com.longtoast.bilbil

import android.app.Application
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk
// 🚨 [추가] 네이버 SDK Import
//import com.navercorp.nid.NaverIdLoginSDK

class MyApplication : Application() {

    private val NATIVE_APP_KEY = "7a3a72c388ba6dfc6df8ca9715f284ff"

    // 🚨 [필수] 네이버 개발자 센터에서 발급받은 실제 키로 변경해야 합니다.
    private val NAVER_CLIENT_ID = "a7CXZxOYZfr0Oz_swkzL"
    private val NAVER_CLIENT_SECRET = "yVqKNPr2R8"
    private val NAVER_CLIENT_NAME = "BilBil" // 앱 이름

    override fun onCreate() {
        super.onCreate()

        // 1. 카카오 로그인 SDK 초기화
        KakaoSdk.init(this, NATIVE_APP_KEY)

        // 2. 카카오 지도 SDK 초기화
        KakaoMapSdk.init(this, NATIVE_APP_KEY)

        // 🚨 3. [추가] 네이버 SDK 초기화
        // 이 코드가 없어서 SDKNotInitializedException이 발생했습니다.
        //NaverIdLoginSDK.initialize(this, NAVER_CLIENT_ID, NAVER_CLIENT_SECRET, NAVER_CLIENT_NAME)

        // 4. AuthTokenManager 초기화
        AuthTokenManager.init(this)
    }
}