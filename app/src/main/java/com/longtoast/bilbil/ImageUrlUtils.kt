package com.longtoast.bilbil

/**
 * 서버에서 내려온 이미지 경로를 실제로 로딩 가능한 절대 URL로 변환합니다.
 * - "/uploads/..." 처럼 슬래시가 없는 상대 경로도 처리
 * - 이미 절대 URL이면 그대로 반환
 * - 데이터 URI(Base64)나 빈 값은 그대로/혹은 null 로 반환
 */
object ImageUrlUtils {
    fun resolve(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        // Base64 또는 data URI는 그대로 사용
        if (raw.startsWith("data:")) return raw

        // 이미 http/https 로 시작하는 절대 경로면 그대로 반환
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw

        val trimmed = raw.trim()
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"

        return ServerConfig.HTTP_BASE_URL.removeSuffix("/") + normalized
    }
}
