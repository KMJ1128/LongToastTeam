package com.longtoast.bilbil

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object AuthTokenManager {

    private const val PREFS_NAME = "AuthPrefs"
    private const val KEY_SERVICE_TOKEN = "serviceToken"
    private const val KEY_USER_ID = "userId"

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
        prefs.edit().remove(KEY_SERVICE_TOKEN).apply()
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
}
