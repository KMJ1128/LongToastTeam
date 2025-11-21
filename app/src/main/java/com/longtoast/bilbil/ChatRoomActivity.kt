package com.longtoast.bilbil

import android.graphics.Bitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.util.Base64
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var webSocket: WebSocket
    private lateinit var recyclerChat: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var buttonAttachImage: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    private var selectedImageUri: Uri? = null

    private val chatMessages = mutableListOf<ChatMessage>()
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>() // 🔑 로컬 메시지 매핑

    private val WEBSOCKET_URL = "ws://172.16.101.190:8080/stomp/chat"
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

        chatAdapter = ChatAdapter(chatMessages, senderId.toString())
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        fetchChatHistory()
        connectWebSocket()
        setupListeners()
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
                val connectFrame = "CONNECT\n" +
                        "accept-version:1.2\n" +
                        "heart-beat:10000,10000\n" +
                        "Authorization:Bearer $token\n" +
                        "\n\u0000"
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

    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                Log.d("STOMP_WS", "🟢 CONNECTED 수신")
                val subscribeFrame = "SUBSCRIBE\n" +
                        "id:sub-0\n" +
                        "destination:/topic/signal/$roomId\n" +
                        "\n\u0000"
                webSocket.send(subscribeFrame)
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
                            // 로컬 메시지와 매칭
                            val matchEntry = tempMessageMap.entries.firstOrNull { it.value.content == receivedMessage.content }
                            if (matchEntry != null) {
                                val index = chatMessages.indexOf(matchEntry.value)
                                if (index != -1) {
                                    chatMessages[index] = receivedMessage
                                    chatAdapter.notifyItemChanged(index)
                                    tempMessageMap.remove(matchEntry.key)
                                    Log.d("CHAT_WS", "✅ 로컬 에코 교체 완료")
                                }
                            } else {
                                chatMessages.add(receivedMessage)
                                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                                recyclerChat.scrollToPosition(chatMessages.size - 1)
                                Log.d("CHAT_WS", "로컬 메시지 미발견, 새로 추가")
                            }
                        } else {
                            chatMessages.add(receivedMessage)
                            chatAdapter.notifyItemInserted(chatMessages.size - 1)
                            recyclerChat.scrollToPosition(chatMessages.size - 1)
                            Log.d("STOMP_WS_UPDATE", "실시간 메시지 추가: Sender ${receivedMessage.senderId}")
                        }
                    } catch (e: Exception) {
                        Log.e("STOMP_MSG", "ChatMessage JSON 파싱 오류", e)
                    }
                }
            }
            else -> Log.d("STOMP_WS", "ℹ️ 기타 프레임: $frame")
        }
    }

    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {
            val finalImageUri = imageUri ?: selectedImageUri
            val base64Image = if (finalImageUri != null) {
                withContext(Dispatchers.IO) { convertUriToBase64(finalImageUri, 40) }
            } else null

            if (content.isEmpty() && base64Image.isNullOrEmpty()) return@launch

            val escapedContent = content.replace("\"", "\\\"")
            val payloadJson = if (base64Image.isNullOrEmpty()) {
                "{\"senderId\":$senderId,\"content\":\"$escapedContent\"}"
            } else {
                "{\"senderId\":$senderId,\"content\":\"$escapedContent\",\"base64Image\":\"$base64Image\"}"
            }

            val messageFrame = "SEND\n" +
                    "destination:/app/signal/$roomId\n" +
                    "content-type:application/json\n" +
                    "\n$payloadJson\u0000"

            webSocket.send(messageFrame)
            Log.d("STOMP_SEND", "📤 메시지 전송 완료. 텍스트 길이: ${content.length}, 이미지 존재: ${base64Image != null}")

            val tempMessage = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = content,
                imageUrl = base64Image,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )

            chatMessages.add(tempMessage)
            tempMessageMap[tempMessage.id] = tempMessage
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recyclerChat.scrollToPosition(chatMessages.size - 1)
            selectedImageUri = null
        }
    }

    private fun convertUriToBase64(uri: Uri, quality: Int): String? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val bytes = outputStream.toByteArray()
                outputStream.close()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            Log.e("BASE64_CONV", "URI to Base64 failed for $uri", e)
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
