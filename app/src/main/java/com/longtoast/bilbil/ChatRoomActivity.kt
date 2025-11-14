// com.longtoast.bilbil.ChatRoomActivity.kt
package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.MsgEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.WebSocket
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var webSocket: WebSocket
    private lateinit var recyclerChat: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    private val chatMessages = mutableListOf<ChatMessage>()

    private val WEBSOCKET_URL = "ws://172.16.102.73:8080/stomp/chat"
    private val roomId by lazy { intent.getStringExtra("ROOM_ID") ?: "1" }

    // 💡 [수정] senderId는 String으로 유지. AuthTokenManager가 Int를 반환하므로 String으로 변환.
    private val senderId: String by lazy {
        val actualId = AuthTokenManager.getUserId()?.toString()
        if (actualId == null) {
            Log.e("CHAT_AUTH_CRITICAL", "❌ 현재 사용자 ID 로드 실패! '1' 사용.")
        }
        actualId ?: "1" // DB에 존재하는 유효한 사용자 ID (String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)

        chatAdapter = ChatAdapter(chatMessages, senderId)
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        fetchChatHistory()
        connectWebSocket()

        buttonSend.setOnClickListener {
            val messageText = editMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                editMessage.text.clear()
            }
        }
    }

    // ... (fetchChatHistory 함수 유지) ...
    private fun fetchChatHistory() {
        RetrofitClient.getApiService().getChatHistory(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: retrofit2.Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful && response.body()?.data != null) {
                        try {
                            val gson = Gson()
                            val listType = object : TypeToken<List<ChatMessage>>() {}.type
                            val historyList: List<ChatMessage> = gson.fromJson(gson.toJson(response.body()?.data), listType)

                            chatMessages.addAll(historyList)
                            chatAdapter.notifyDataSetChanged()
                            if (chatMessages.isNotEmpty()) {
                                recyclerChat.scrollToPosition(chatMessages.size - 1)
                            }
                            Log.d("CHAT_HISTORY", "✅ 채팅 내역 ${historyList.size}개 로드 성공. Current User ID: $senderId")

                        } catch (e: Exception) {
                            Log.e("CHAT_HISTORY", "채팅 내역 파싱 중 오류 발생", e)
                        }
                    } else {
                        Log.e("CHAT_HISTORY", "내역 조회 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                    }
                }
                override fun onFailure(call: retrofit2.Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_HISTORY", "네트워크 오류", t)
                }
            })
    }


    // ... (connectWebSocket 함수 유지) ...
    private fun connectWebSocket() {
        val token = AuthTokenManager.getToken()
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(WEBSOCKET_URL)
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("STOMP_WS", "✅ WebSocket 연결 성공")
                val connectFrame = "CONNECT\n" +
                        "accept-version:1.2\n" +
                        "heart-beat:10000,10000\n" +
                        "Authorization:Bearer $token\n" +
                        "\n" +
                        "\u0000"
                webSocket.send(connectFrame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("STOMP_WS_RECV", "📩 수신: $text")
                runOnUiThread { handleStompFrame(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("STOMP_WS", "❌ WebSocket 오류: ${t.message}")
                runOnUiThread {
                    Toast.makeText(this@ChatRoomActivity, "서버 연결 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("STOMP_WS", "연결 종료: $reason")
                webSocket.close(1000, null)
            }
        })
    }

    /**
     * STOMP 프레임 처리 (CONNECTED, MESSAGE)
     */
    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                Log.d("STOMP_WS", "🟢 CONNECTED 수신")

                val subscribeFrame = "SUBSCRIBE\n" +
                        "id:sub-0\n" +
                        "destination:/topic/signal/$roomId\n" +
                        "\n" +
                        "\u0000"
                webSocket.send(subscribeFrame)
                Log.d("STOMP_WS", "📡 채팅방 구독 완료: /topic/signal/$roomId")
            }

            frame.startsWith("MESSAGE") -> {
                val parts = frame.split("\n\n")
                if (parts.size > 1) {
                    val payload = parts[1].replace("\u0000", "")
                    Log.d("STOMP_MSG", "💬 서버 메시지 본문: $payload")

                    try {
                        val gson = Gson()
                        val message = gson.fromJson(payload, ChatMessage::class.java)

                        // 💡 [핵심] 브로드캐스트 메시지 수신 시 로컬 에코를 방지하기 위해 무시 (중복 표시 방지)
                        // 이 로직이 작동하려면, sendMessage에서 로컬 에코를 활성화해야 합니다.
                        // 현재는 로컬 에코를 사용하므로, 이 부분은 무시하고 로컬 에코만 사용합니다.
                        Log.d("STOMP_WS", "🔄 서버 브로드캐스트 수신 완료. 로컬 에코 사용 중이므로 무시합니다.")
                    } catch (e: Exception) {
                        Log.e("STOMP_MSG", "ChatMessage JSON 파싱 오류", e)
                    }
                }
            }

            else -> Log.d("STOMP_WS", "ℹ️ 기타 프레임: $frame")
        }
    }

    /**
     * 메시지 전송 (STOMP SEND)
     */
    private fun sendMessage(content: String) {
        val escapedContent = content.replace("\"", "\\\"")

        // 1. STOMP 프레임 전송 (senderId는 String으로 전송)
        val messageFrame = "SEND\n" +
                "destination:/app/signal/$roomId\n" +
                "content-type:application/json\n" +
                "\n" +
                "{\"senderId\":\"$senderId\",\"content\":\"$escapedContent\"}" +
                "\u0000"

        webSocket.send(messageFrame)
        Log.d("STOMP_SEND", "📤 메시지 전송 완료 → /app/signal/$roomId: $content")

        // 2. 🔑 [핵심] 로컬 에코 복원 (메시지 전송 시 즉시 화면에 표시)
        val tempMessage = ChatMessage(
            id = System.currentTimeMillis(),
            roomId = roomId,
            // 💡 [수정] DTO 타입에 맞춰 String을 Int로 변환하여 임시 메시지 생성
            senderId = senderId.toIntOrNull() ?: 0,
            content = content,
            imageUrl = null,
            sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        )

        chatMessages.add(tempMessage)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        recyclerChat.scrollToPosition(chatMessages.size - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
            Log.d("STOMP_WS", "WebSocket 종료")
        }
    }
}