package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // 🚨 CoroutineScope를 액티비티 생명주기에 연결
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.ActivityChatRoomBinding
import com.longtoast.bilbil.dto.ChatMessage
import com.google.gson.Gson

// 🚨 [필수 임포트] Krossbow 및 Coroutines
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import org.hildan.krossbow.stomp.converters.StompJmsBodyConverter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatRoomBinding
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // 🚨 Krossbow 및 STOMP 관련 변수
    private val stompClient = StompClient()
    private var stompSession: StompSession? = null // 현재 활성화된 세션
    private val WEBSOCKET_URL = "ws://172.16.102.62:8080/ws/websocket"
    private val GSON = Gson()

    private var roomId: String? = null
    private var productId: String? = null
    private var sellerNickname: String? = null

    // 🚨 TODO: 실제 유저 ID로 대체
    private val currentUserId = "2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Intent에서 데이터 가져오기 (생략)
        roomId = intent.getStringExtra("ROOM_ID")
        // ... (생략)

        if (roomId == null) {
            // ... (종료 로직 생략)
            return
        }

        setupToolbar()
        setupRecyclerView()

        // 🚨 [핵심] STOMP 연결 시작 (lifecycleScope 사용)
        connectStomp()

        // 4. 전송 버튼 리스너 설정
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }
    }

    // ----------------------------------------------------
    // 🚨 [핵심] STOMP 연결, 구독 및 해제 로직 (Coroutine 기반)
    // ----------------------------------------------------
    private fun connectStomp() {
        lifecycleScope.launch {
            try {
                // 1. WebSocket 연결 및 STOMP 세션 생성
                stompSession = stompClient.connect(WEBSOCKET_URL)
                Log.d("KROSSBOW_STOMP", "✅ STOMP 연결 및 세션 생성 성공")

                // 2. 주제 구독
                subscribeTopic(stompSession!!)

            } catch (e: CancellationException) {
                Log.d("KROSSBOW_STOMP", "연결 작업 취소됨 (액티비티 종료 등): ${e.message}")
            } catch (e: Exception) {
                Log.e("KROSSBOW_STOMP", "❌ STOMP 연결 오류", e)
                runOnUiThread {
                    Toast.makeText(this@ChatRoomActivity, "채팅 서버 연결 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun subscribeTopic(session: StompSession) {
        if (roomId == null) return

        lifecycleScope.launch {
            try {
                // 💡 /topic/signal/{roomId} 주제 구독
                session.subscribe("/topic/signal/$roomId")
                    // 수신되는 메시지를 ChatMessage DTO로 변환
                    .onEach { stompMessage ->
                        handleIncomingMessage(stompMessage.body)
                    }
                    .collect() // 메시지 수신 흐름을 계속 유지

            } catch (e: Exception) {
                Log.e("KROSSBOW_STOMP", "❌ 구독 중 오류 발생", e)
            }
        }
    }

    private fun handleIncomingMessage(jsonPayload: String) {
        try {
            // JSON Payload를 ChatMessage DTO로 변환
            val chatMessage = GSON.fromJson(jsonPayload, ChatMessage::class.java)

            // UI 업데이트
            runOnUiThread {
                Log.d("KROSSBOW_RECV", "메시지 수신: ${chatMessage.content}")
                addMessageToChat(chatMessage)
            }
        } catch (e: Exception) {
            Log.e("KROSSBOW_RECV", "수신 메시지 파싱 오류", e)
        }
    }

    /**
     * STOMP SEND 명령을 실행합니다.
     */
    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        val session = stompSession

        if (messageText.isNotEmpty() && session != null) {
            lifecycleScope.launch {
                try {
                    // 1. 전송용 JSON DTO 생성 (백엔드 ChatWebSocketController의 ClientMessage DTO 구조)
                    val clientMessagePayload = mapOf(
                        "senderId" to currentUserId,
                        "content" to messageText,
                        "imageUrl" to null
                    )

                    val jsonPayload = GSON.toJson(clientMessagePayload)

                    // 2. 서버의 /app/signal/{roomId} 엔드포인트로 메시지 전송
                    session.send("/app/signal/$roomId", jsonPayload).join() // join()으로 전송 완료까지 기다림
                    Log.d("KROSSBOW_SEND", "메시지 전송 성공: $messageText")

                    // 3. 입력창 비우기
                    binding.editTextMessage.text.clear()

                } catch (e: Exception) {
                    Log.e("KROSSBOW_SEND", "❌ 메시지 전송 오류", e)
                    runOnUiThread {
                        Toast.makeText(this@ChatRoomActivity, "메시지 전송 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (session == null) {
            Toast.makeText(this, "서버와 연결 중입니다. 잠시 후 다시 시도하세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        // ... (생략) ...
        chatAdapter = ChatAdapter(chatMessages, currentUserId)
        binding.recyclerViewChat.apply {
            layoutManager = LinearLayoutManager(this@ChatRoomActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun addMessageToChat(message: ChatMessage) {
        // ... (생략) ...
        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.recyclerViewChat.scrollToPosition(chatMessages.size - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 🚨 액티비티 종료 시 CoroutineScope가 자동으로 취소되지만, 세션을 명시적으로 닫아줍니다.
        stompSession?.close()
        Log.d("KROSSBOW_STOMP", "STOMP 세션 해제 완료")
    }

    // ... (setupToolbar, onOptionsItemSelected 등 기존 함수는 유지) ...
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.title = sellerNickname
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}