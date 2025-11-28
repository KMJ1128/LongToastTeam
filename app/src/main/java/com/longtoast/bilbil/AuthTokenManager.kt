package com.longtoast.bilbil

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object AuthTokenManager {

    private const val PREFS_NAME = "AuthPrefs"
    private const val KEY_SERVICE_TOKEN = "serviceToken"
    private const val KEY_USER_ID = "userId"
    private const val KEY_USER_NICKNAME = "USER_NICKNAME"
    private const val KEY_USER_ADDRESS = "USER_ADDRESS"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_SERVICE_TOKEN, token).apply()
        Log.d("AuthTokenManager", "토큰 저장 성공")
    }

    fun getToken(): String? {
        return prefs.getString(KEY_SERVICE_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_SERVICE_TOKEN)
            .remove(KEY_USER_ID)
            //.remove(KEY_USER_NICKNAME)
            //.remove(KEY_USER_ADDRESS)
            .apply()
    }

    // ✅ [추가] userId 저장
    fun saveUserId(userId: Int) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply()
        Log.d("AuthTokenManager", "userId 저장 성공: $userId")
    }

    // ✅ [추가] userId 불러오기
    fun getUserId(): Int? {
        val id = prefs.getInt(KEY_USER_ID, -1)
        return if (id != -1) id else null
    }

    fun clearUserId() {
        prefs.edit().remove(KEY_USER_ID).apply()

    }


    // 닉네임 저장 메서드
    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_USER_NICKNAME, nickname).apply()
    }

    // 닉네임 가져오기 메서드
    fun getNickname(): String? {
        return prefs.getString(KEY_USER_NICKNAME, null)
    }

    // 주소 저장 메서드
    fun saveAddress(address: String) {
        prefs.edit().putString(KEY_USER_ADDRESS, address).apply()
    }

    // 주소 가져오기 메서드
    fun getAddress(): String? {
        return prefs.getString(KEY_USER_ADDRESS, null)
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_SERVICE_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NICKNAME)
            .remove(KEY_USER_ADDRESS)
            .apply()
        Log.d("AuthTokenManager", "모든 인증 정보 삭제 완료")
    }
}
