package com.longtoast.bilbil

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 🚨 네이티브 앱 키 사용 🚨
        KakaoSdk.init(this, "7a3a72c388ba6dfc6df8ca9715f284ff")
    }
}