// com.longtoast.bilbil.ChatRoomActivity.kt
package com.longtoast.bilbil

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.MsgEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
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
    private lateinit var buttonAttachImage: ImageButton
    private lateinit var chatAdapter: ChatAdapter

    private var selectedImageUri: Uri? = null

    private val chatMessages = mutableListOf<ChatMessage>()

    /**
     * 서버에서 에코로 돌려주는 메시지와 로컬 임시 메시지를 매칭하기 위한 맵.
     * key: clientTempId (음수 임시 ID)
     */
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>()

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL

    private val roomId: Int by lazy {
        intent.getIntExtra("ROOM_ID", -1)
    }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

    /**
     * 로컬에서만 사용하는 임시 메시지 ID (음수로 감소)
     */
    private var nextTempId = -1L

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // 현재 입력된 텍스트와 함께 이미지 메시지 전송
            sendMessage(editMessage.text.toString().trim(), it)
            editMessage.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        if (roomId <= 0) {
            Toast.makeText(this, "유효하지 않은 채팅방입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        chatAdapter = ChatAdapter(chatMessages, senderId)
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
                        "Authorization: Bearer $token\n" +
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
                            // 🔑 서버 에코 메시지 → 로컬 임시 메시지와 매칭
                            val tempId = receivedMessage.clientTempId
                            if (tempId != null) {
                                val localMessage = tempMessageMap[tempId]
                                if (localMessage != null) {
                                    val index = chatMessages.indexOf(localMessage)
                                    if (index != -1) {
                                        chatMessages[index] = receivedMessage
                                        chatAdapter.notifyItemChanged(index)
                                        recyclerChat.scrollToPosition(index)
                                        Log.d("CHAT_WS", "✅ clientTempId 기반 로컬 에코 교체 완료: tempId=$tempId")
                                    }
                                    tempMessageMap.remove(tempId)
                                } else {
                                    // 혹시 맵에서 못 찾으면 그냥 뒤에 추가
                                    chatMessages.add(receivedMessage)
                                    chatAdapter.notifyItemInserted(chatMessages.size - 1)
                                    recyclerChat.scrollToPosition(chatMessages.size - 1)
                                    Log.d("CHAT_WS", "로컬 tempId 매칭 실패, 새로 추가: tempId=$tempId")
                                }
                            } else {
                                // 예전 메시지 형식 등 clientTempId가 없는 경우 fallback
                                val matchEntry = tempMessageMap.entries.firstOrNull { (_, value) ->
                                    value.content == receivedMessage.content &&
                                            value.imageUrl == receivedMessage.imageUrl
                                }
                                if (matchEntry != null) {
                                    val index = chatMessages.indexOf(matchEntry.value)
                                    if (index != -1) {
                                        chatMessages[index] = receivedMessage
                                        chatAdapter.notifyItemChanged(index)
                                        recyclerChat.scrollToPosition(index)
                                        Log.d("CHAT_WS", "✅ content+imageUrl 기반 로컬 에코 교체 완료 (fallback)")
                                    }
                                    tempMessageMap.remove(matchEntry.key)
                                } else {
                                    chatMessages.add(receivedMessage)
                                    chatAdapter.notifyItemInserted(chatMessages.size - 1)
                                    recyclerChat.scrollToPosition(chatMessages.size - 1)
                                    Log.d("CHAT_WS", "로컬 메시지 미발견, 새로 추가 (no clientTempId)")
                                }
                            }
                        } else {
                            // 상대방이 보낸 메시지
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
            if (roomId <= 0) {
                Toast.makeText(this@ChatRoomActivity, "유효하지 않은 채팅방입니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val trimmedContent = content.trim()
            val targetImageUri = imageUri ?: selectedImageUri

            var uploadedImageUrl: String? = null
            if (targetImageUri != null) {
                uploadedImageUrl = uploadImageForRoom(targetImageUri)
                if (uploadedImageUrl == null) {
                    Toast.makeText(this@ChatRoomActivity, "이미지 업로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            if (trimmedContent.isEmpty() && uploadedImageUrl.isNullOrBlank()) return@launch

            // 🔑 로컬에서만 사용하는 임시 ID 생성
            val tempId = nextTempId--

            val payload = mapOf(
                "senderId" to senderId,
                "content" to trimmedContent,
                "imageUrl" to uploadedImageUrl,
                "clientTempId" to tempId   // 서버에 같이 보내서 에코 매칭용으로 사용
            )
            val payloadJson = Gson().toJson(payload)

            val messageFrame = "SEND\n" +
                    "destination:/app/signal/$roomId\n" +
                    "content-type:application/json\n" +
                    "\n$payloadJson\u0000"

            webSocket.send(messageFrame)
            Log.d(
                "STOMP_SEND",
                "📤 메시지 전송 완료. 텍스트 길이: ${trimmedContent.length}, 이미지 존재: ${uploadedImageUrl != null}"
            )

            val tempMessage = ChatMessage(
                id = tempId,  // 서버 ID 나오기 전이라 음수 임시 ID 사용
                roomId = roomId,
                senderId = senderId,
                content = if (trimmedContent.isNotEmpty()) trimmedContent else null,
                imageUrl = uploadedImageUrl,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                isRead = false,
                clientTempId = tempId
            )

            chatMessages.add(tempMessage)
            tempMessageMap[tempId] = tempMessage
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recyclerChat.scrollToPosition(chatMessages.size - 1)
            selectedImageUri = null
        }
    }

    private suspend fun uploadImageForRoom(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
                val bytes = inputStream.readBytes()
                inputStream.close()

                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(
                    "image",
                    "chat_${senderId}_${System.currentTimeMillis()}.jpg",
                    requestBody
                )

                val response = RetrofitClient.getApiService().uploadChatImage(roomId, part)
                if (response.isSuccessful) {
                    val rawData = response.body()?.data
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val mapData: Map<String, String>? = gson.fromJson(gson.toJson(rawData), type)
                    mapData?.get("imageUrl")
                } else {
                    Log.e(
                        "CHAT_IMAGE_UPLOAD",
                        "이미지 업로드 실패: ${response.code()} ${response.errorBody()?.string()}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e("CHAT_IMAGE_UPLOAD", "이미지 업로드 중 오류", e)
                null
            }
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
