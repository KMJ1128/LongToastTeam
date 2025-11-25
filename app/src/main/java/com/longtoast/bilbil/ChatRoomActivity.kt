package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.longtoast.bilbil.ServerConfig
import com.longtoast.bilbil.util.ImageUtil
import com.longtoast.bilbil.util.RemoteImageLoader
import okhttp3.MultipartBody
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var webSocket: WebSocket
    private lateinit var recyclerChat: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var buttonAttachImage: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    private var selectedImageUri: Uri? = null
    private var partnerNickname: String? = null
    private var partnerProfileImageUrl: String? = null

    private val chatMessages = mutableListOf<ChatMessage>()
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>() // 🔑 로컬 메시지 매핑

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private val roomId by lazy { intent.getStringExtra("ROOM_ID") ?: "1" }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

    private var nextTempId = -1L // 로컬 임시 ID 시작

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            sendMessage(editMessage.text.toString().trim(), it)
            editMessage.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        partnerNickname = intent.getStringExtra("PARTNER_NICKNAME")
            ?: intent.getStringExtra("SELLER_NICKNAME")
        partnerProfileImageUrl = intent.getStringExtra("PARTNER_PROFILE")

        chatAdapter = ChatAdapter(chatMessages, senderId.toString(), partnerNickname, partnerProfileImageUrl)
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        setupToolbar()

        fetchChatHistory()
        connectWebSocket()
        setupListeners()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_chat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val nameText: TextView? = toolbar.findViewById(R.id.text_toolbar_partner)
        val profileImage: ImageView? = toolbar.findViewById(R.id.image_toolbar_partner)

        nameText?.text = partnerNickname ?: "상대방"
        profileImage?.let {
            RemoteImageLoader.load(it, partnerProfileImageUrl, R.drawable.no_profile)
        }
    }

    private fun setupListeners() {
        buttonSend.setOnClickListener {
            val messageText = editMessage.text.toString().trim()
            if (messageText.isNotEmpty() || selectedImageUri != null) {
                sendMessage(messageText, selectedImageUri)
                editMessage.text.clear()
            }
        }

        buttonAttachImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun fetchChatHistory() {
        RetrofitClient.getApiService().getChatHistory(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: retrofit2.Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful && response.body()?.data != null) {
                        try {
                            val gson = Gson()
                            val listType = object : TypeToken<List<ChatMessage>>() {}.type
                            val historyList: List<ChatMessage> = gson.fromJson(
                                gson.toJson(response.body()?.data),
                                listType
                            )

                            chatMessages.addAll(historyList)
                            chatAdapter.submitMessages(chatMessages)
                            if (chatMessages.isNotEmpty()) {
                                recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                            Log.d("CHAT_HISTORY", "✅ 채팅 내역 ${historyList.size}개 로드 성공. Current User ID: $senderId")
                        } catch (e: Exception) {
                            Log.e("CHAT_HISTORY", "채팅 내역 파싱 중 오류 발생", e)
                        }
                    } else {
                        Log.e("CHAT_HISTORY", "내역 조회 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                        if (response.code() == 401 || response.code() == 403) {
                            Toast.makeText(this@ChatRoomActivity, "세션 만료: 로그인을 다시 해주세요.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onFailure(call: retrofit2.Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_HISTORY", "네트워크 오류", t)
                }
            })
    }

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
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("STOMP_WS_RECV", "📩 수신: $text")
                runOnUiThread { handleSockJsFrame(text) }
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

    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                Log.d("STOMP_WS", "🟢 CONNECTED 수신")
                val subscribeFrame = "SUBSCRIBE\n" +
                        "id:sub-0\n" +
                        "destination:/topic/signal/$roomId\n" +
                        "\n\u0000"
                webSocket.send(wrapSockJsFrame(subscribeFrame))
                Log.d("STOMP_WS", "📡 채팅방 구독 완료")
            }
            frame.startsWith("MESSAGE") -> {
                val parts = frame.split("\n\n")
                if (parts.size > 1) {
                    val payload = parts[1].replace("\u0000", "")
                    Log.d("STOMP_MSG", "💬 서버 메시지 본문: $payload")
                    try {
                        val gson = Gson()
                        val receivedMessage = gson.fromJson(payload, ChatMessage::class.java)

                        if (receivedMessage.senderId == senderId) {
                            val matchEntry = tempMessageMap.entries.firstOrNull {
                                it.value.content == receivedMessage.content ||
                                        (!it.value.imageUrl.isNullOrBlank() && it.value.imageUrl == receivedMessage.imageUrl)
                            }
                            if (matchEntry != null) {
                                val index = chatMessages.indexOf(matchEntry.value)
                                if (index != -1) {
                                    chatMessages[index] = receivedMessage
                                    tempMessageMap.remove(matchEntry.key)
                                    Log.d("CHAT_WS", "✅ 로컬 에코 교체 완료")
                                }
                            } else {
                                chatMessages.add(receivedMessage)
                                Log.d("CHAT_WS", "로컬 메시지 미발견, 새로 추가")
                            }
                        } else {
                            chatMessages.add(receivedMessage)
                            Log.d("STOMP_WS_UPDATE", "실시간 메시지 추가: Sender ${receivedMessage.senderId}")
                        }

                        chatAdapter.submitMessages(chatMessages)
                        recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
                    } catch (e: Exception) {
                        Log.e("STOMP_MSG", "ChatMessage JSON 파싱 오류", e)
                    }
                }
            }
            else -> Log.d("STOMP_WS", "ℹ️ 기타 프레임: $frame")
        }
    }

    /**
     * SockJS는 STOMP 프레임을 JSON 배열로 감싸 전달한다. 수신/송신 모두 이를 맞춰 처리한다.
     */
    private fun handleSockJsFrame(raw: String) {
        if (raw == "o") {
            sendConnectFrame()
            return
        }

        if (raw == "h") return // heartbeat 패킷

        if (raw.startsWith("a[")) {
            try {
                val frames: List<String> = Gson().fromJson(raw.substring(1), object : TypeToken<List<String>>() {}.type)
                frames.forEach { handleStompFrame(it) }
            } catch (e: Exception) {
                Log.e("STOMP_SOCKJS", "SockJS 배열 파싱 실패: $raw", e)
            }
            return
        }

        handleStompFrame(raw)
    }

    private fun wrapSockJsFrame(frame: String): String {
        val escaped = frame
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\u0000", "\\u0000")
        return "[\"$escaped\"]"
    }

    private fun sendConnectFrame() {
        val token = AuthTokenManager.getToken()
        val connectFrame = "CONNECT\n" +
                "accept-version:1.2\n" +
                "heart-beat:10000,10000\n" +
                (if (!token.isNullOrBlank()) "Authorization:Bearer $token\n" else "") +
                "\n\u0000"
        webSocket.send(wrapSockJsFrame(connectFrame))
        Log.d("STOMP_WS", "🚀 CONNECT 프레임 전송")
    }

    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {
            val finalImageUri = imageUri ?: selectedImageUri
            val trimmedContent = content.trim()

            var uploadedImageUrl: String? = null
            if (finalImageUri != null) {
                uploadedImageUrl = uploadImageForChat(finalImageUri)
                if (uploadedImageUrl.isNullOrEmpty()) {
                    Toast.makeText(this@ChatRoomActivity, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            if (trimmedContent.isEmpty() && uploadedImageUrl.isNullOrEmpty()) return@launch

            val escapedContent = trimmedContent.replace("\"", "\\\"")
            val payloadJson = buildString {
                append("{\"senderId\":$senderId")
                if (escapedContent.isNotEmpty()) append(",\"content\":\"$escapedContent\"")
                if (!uploadedImageUrl.isNullOrEmpty()) append(",\"imageUrl\":\"$uploadedImageUrl\"")
                append("}")
            }

            val messageFrame = "SEND\n" +
                    "destination:/app/signal/$roomId\n" +
                    "content-type:application/json\n" +
                    "\n$payloadJson\u0000"

            webSocket.send(wrapSockJsFrame(messageFrame))
            Log.d("STOMP_SEND", "📤 메시지 전송 완료. 텍스트 길이: ${trimmedContent.length}, 이미지 존재: ${uploadedImageUrl != null}")

            val tempMessage = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = trimmedContent.ifEmpty { null },
                imageUrl = uploadedImageUrl,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )

            chatMessages.add(tempMessage)
            tempMessageMap[tempMessage.id] = tempMessage
            chatAdapter.submitMessages(chatMessages)
            recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
            selectedImageUri = null
        }
    }

    private suspend fun uploadImageForChat(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val part: MultipartBody.Part = ImageUtil.uriToMultipart(this@ChatRoomActivity, uri, "image")
                ?: return@withContext null
            val response = RetrofitClient.getApiService().uploadChatImage(roomId, part).execute()
            if (!response.isSuccessful) return@withContext null

            val rawData = response.body()?.data ?: return@withContext null
            return@withContext when (rawData) {
                is String -> rawData
                is Map<*, *> -> rawData["imageUrl"] as? String
                else -> Gson().fromJson(Gson().toJson(rawData), Map::class.java)["imageUrl"] as? String
            }
        } catch (e: Exception) {
            Log.e("CHAT_IMAGE_UPLOAD", "이미지 업로드 실패", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
            Log.d("STOMP_WS", "WebSocket 종료")
        }
    }
}
