package com.longtoast.bilbil

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * SharedPreferences를 사용해 서비스 토큰을 관리하는 싱글톤 객체
 */
object AuthTokenManager {

    private const val PREFS_NAME = "AuthPrefs"
    private const val KEY_SERVICE_TOKEN = "serviceToken"

    private lateinit var prefs: SharedPreferences

    /**
     * 앱 시작 시 MyApplication에서 호출되어야 함
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 서버에서 받은 서비스 토큰 저장
     */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_SERVICE_TOKEN, token).apply()
        Log.d("AuthTokenManager", "토큰 저장 성공")
    }

    /**
     * 저장된 토큰 불러오기 (없으면 null 반환)
     */
    fun getToken(): String? {
        return prefs.getString(KEY_SERVICE_TOKEN, null)
    }

    /**
     * 토큰 삭제 (로그아웃 시 사용)
     */
    fun clearToken() {
        prefs.edit().remove(KEY_SERVICE_TOKEN).apply()
    }
}