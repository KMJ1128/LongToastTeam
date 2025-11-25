package com.longtoast.bilbil.util

import com.longtoast.bilbil.ServerConfig

/**
 * 서버에서 내려오는 이미지 경로가 "/uploads/..." 같은 상대 경로일 때
 * Retrofit 기본 주소(ServerConfig.HTTP_BASE_URL)와 합쳐서 절대 경로로 만들어준다.
 */
object ImageUrlUtils {
    fun buildFullUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null

        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }

        val base = ServerConfig.HTTP_BASE_URL.removeSuffix("/")
        return if (rawUrl.startsWith("/")) {
            base + rawUrl
        } else {
            "$base/$rawUrl"
        }
    }

    fun buildFullUrls(rawUrls: List<String>?): List<String> =
        rawUrls?.mapNotNull { buildFullUrl(it) } ?: emptyList()
}
