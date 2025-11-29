package com.longtoast.bilbil

/**
 * 서버 엔드포인트를 한 곳에서 관리합니다.
 * HTTP_BASE_URL만 실제 서버 주소로 맞춰주면 Retrofit과 WebSocket 모두 동일하게 적용됩니다.
 */

object ServerConfig {
    /** HTTP(S) API 기본 주소는 반드시 `/` 로 끝나야 합니다. */

    //const val HTTP_BASE_URL = "https://unpaneled-jennette-phonily.ngrok-free.dev/"
    //const val HTTP_BASE_URL = "http://192.168.45.105:8080/"
    //const val HTTP_BASE_URL = "http://172.16.101.164:8080/"
    const val HTTP_BASE_URL = "http://192.168.45.105:8080/"


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

            return "$wsBase/stomp/chat/websocket"
        }
}