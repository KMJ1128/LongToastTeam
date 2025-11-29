package com.longtoast.bilbil

import android.util.Log

/**
 * 서버에서 내려온 이미지 경로를 실제로 로딩 가능한 절대 URL로 변환합니다.
 * - "/uploads/..." 처럼 슬래시가 없는 상대 경로도 처리
 * - 이미 절대 URL이면 그대로 반환
 * - 데이터 URI(Base64)나 빈 값은 그대로/혹은 null 로 반환
 */
object ImageUrlUtils {

    private const val TAG = "ImageUrlUtils"

    fun resolve(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // 이미 절대 URL 이면 그대로 사용
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            Log.d(TAG, "이미 절대 URL → $raw")
            return raw
        }

        // IMG_BASE_URL 뒤에 붙은 슬래시 제거
        val base = ServerConfig.IMG_BASE_URL.removeSuffix("/")

        // path 앞에 슬래시 하나만 강제
        val path = if (raw.startsWith("/")) raw else "/$raw"

        val resolved = base + path
        Log.d(TAG, "resolve() 결과: base=$base, raw=$raw, resolved=$resolved")
        return resolved
    }
}
