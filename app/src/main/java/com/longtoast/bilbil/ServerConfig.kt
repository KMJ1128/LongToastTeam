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
     * SockJS를 사용하지만 네이티브 WebSocket을 쓸 때는 `/websocket` 서픽스를 붙여야 연결됩니다.
     */
    val WEBSOCKET_URL: String
        get() {
            val normalizedBase = HTTP_BASE_URL.removeSuffix("/")

            // 💡 [통합] master 브랜치의 안전한 프로토콜 변환 로직 채택 (https -> wss, http -> ws)
            val wsBase = when {
                normalizedBase.startsWith("https://") ->
                    normalizedBase.replaceFirst("https://", "wss://")
                normalizedBase.startsWith("http://") ->
                    normalizedBase.replaceFirst("http://", "ws://")
                else -> normalizedBase
            }

            return "$wsBase/stomp/chat/websocket"
        }
}