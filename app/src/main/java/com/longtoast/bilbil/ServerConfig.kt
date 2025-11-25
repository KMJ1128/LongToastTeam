package com.longtoast.bilbil

/**
 * 서버 엔드포인트를 한 곳에서 관리합니다.
 * HTTP_BASE_URL만 실제 서버 주소로 맞춰주면 Retrofit과 WebSocket 모두 동일하게 적용됩니다.
 */
object ServerConfig {
    /** HTTP(S) API 기본 주소는 반드시 `/` 로 끝나야 합니다. */
    // 현재 사용 중인 ngrok 주소를 그대로 유지
    const val HTTP_BASE_URL = "https://unpaneled-jennette-phonily.ngrok-free.dev/"

    /**
     * Spring STOMP WebSocket 엔드포인트.
     * 서버 설정(WebSocketConfig.registerStompEndpoints)과 동일하게 `/stomp/chat`으로 연결합니다.
     */
    val WEBSOCKET_URL: String
        get() {
            val normalizedBase = HTTP_BASE_URL.removeSuffix("/")

            val wsBase = when {
                normalizedBase.startsWith("https://") ->
                    normalizedBase.replaceFirst("https://", "wss://")
                normalizedBase.startsWith("http://") ->
                    normalizedBase.replaceFirst("http://", "ws://")
                else -> normalizedBase
            }

            return "$wsBase/stomp/chat"
        }
}