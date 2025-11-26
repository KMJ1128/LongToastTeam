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
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.RentalActionPayload
import com.longtoast.bilbil.dto.RentalDecisionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Call
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
    private lateinit var toolbar: MaterialToolbar

    private var selectedImageUri: Uri? = null

    private val chatMessages = mutableListOf<ChatMessage>()
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>() // 🔑 로컬 메시지 매핑

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private val roomId by lazy { intent.getStringExtra("ROOM_ID") ?: "1" }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }
    private val productId: Int? by lazy {
        val numeric = intent.getIntExtra("PRODUCT_ID", -1)
        if (numeric > 0) numeric else intent.getStringExtra("PRODUCT_ID")?.toIntOrNull()
    }
    private val productTitle: String? by lazy { intent.getStringExtra("PRODUCT_TITLE") }
    private val productPrice: Int by lazy { intent.getIntExtra("PRODUCT_PRICE", 0) }
    private val productDeposit: Int by lazy { intent.getIntExtra("PRODUCT_DEPOSIT", 0) }
    private val lenderId: Int by lazy { intent.getIntExtra("LENDER_ID", -1) }

    private var nextTempId = -1L // 로컬 임시 ID 시작

    // 갤러리에서 이미지 선택
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

        toolbar = findViewById(R.id.toolbar_chat)
        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        setupToolbar()

        chatAdapter = ChatAdapter(chatMessages, senderId.toString()) { payload ->
            confirmRental(payload)
        }
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        fetchChatHistory()
        connectWebSocket()
        setupListeners()
    }

    private fun setupToolbar() {
        toolbar.title = intent.getStringExtra("SELLER_NICKNAME") ?: "채팅"
        toolbar.inflateMenu(R.menu.menu_chat_room)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_rent_request) {
                openRentRequestForm()
                true
            } else {
                false
            }
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

    /**
     * 1) 채팅방 입장 시 이전 채팅 내역 불러오기
     */
    private fun fetchChatHistory() {
        RetrofitClient.getApiService().getChatHistory(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
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
                        Log.e(
                            "CHAT_HISTORY",
                            "내역 조회 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}"
                        )
                        if (response.code() == 401 || response.code() == 403) {
                            Toast.makeText(
                                this@ChatRoomActivity,
                                "세션 만료: 로그인을 다시 해주세요.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_HISTORY", "네트워크 오류", t)
                }
            })
    }

    /**
     * 2) WebSocket(STOMP) 연결
     */
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
                    Toast.makeText(
                        this@ChatRoomActivity,
                        "서버 연결 실패: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("STOMP_WS", "연결 종료: $reason")
                webSocket.close(1000, null)
            }
        })
    }

    /**
     * 3) STOMP 프레임 처리
     */
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
                            // 🔁 로컬에서 먼저 추가한 메시지와 매칭 (텍스트+이미지 둘 다 비교)
                            val matchEntry = tempMessageMap.entries.firstOrNull {
                                it.value.content == receivedMessage.content &&
                                        it.value.imageUrl == receivedMessage.imageUrl
                            }
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
                            // 상대방 메시지
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

    /**
     * 4) 메시지 전송
     *   - 이미지가 있다면: 먼저 REST로 업로드 → imageUrl 반환받고 → WebSocket으로 imageUrl 전송
     *   - 텍스트만 있다면: 바로 WebSocket으로 content만 전송
     */
    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {
            val finalImageUri = imageUri ?: selectedImageUri
            var imageUrl: String? = null

            // 4-1. 이미지가 있으면 먼저 업로드
            if (finalImageUri != null) {
                imageUrl = uploadChatImage(finalImageUri)
                if (imageUrl == null) {
                    Toast.makeText(
                        this@ChatRoomActivity,
                        "이미지 업로드에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // 4-2. 텍스트도 없고 이미지 URL도 없으면 전송 안 함
            if (content.isEmpty() && imageUrl.isNullOrEmpty()) {
                return@launch
            }

            // 4-3. WebSocket으로 보낼 JSON payload 구성
            val escapedContent = content.replace("\"", "\\\"")
            val payloadJson = buildString {
                append("{\"senderId\":$senderId")
                if (escapedContent.isNotEmpty()) {
                    append(",\"content\":\"$escapedContent\"")
                }
                if (!imageUrl.isNullOrEmpty()) {
                    append(",\"imageUrl\":\"$imageUrl\"")
                }
                append("}")
            }

            val messageFrame = "SEND\n" +
                    "destination:/app/signal/$roomId\n" +
                    "content-type:application/json\n" +
                    "\n$payloadJson\u0000"

            webSocket.send(messageFrame)
            Log.d(
                "STOMP_SEND",
                "📤 메시지 전송 완료. 텍스트 길이: ${content.length}, 이미지 URL 존재: ${!imageUrl.isNullOrEmpty()}"
            )

            // 4-4. 화면에 일단 먼저 표시 (임시 ID로 추가 후, 서버 에코 시 교체)
            val tempMessage = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = if (content.isNotEmpty()) content else null,
                imageUrl = imageUrl,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )

            chatMessages.add(tempMessage)
            tempMessageMap[tempMessage.id] = tempMessage
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recyclerChat.scrollToPosition(chatMessages.size - 1)

            // 선택된 이미지 초기화
            selectedImageUri = null
        }
    }

    /**
     * 5) 이미지 업로드 REST 호출
     *   - POST /api/chat/room/{roomId}/image
     *   - Multipart: image
     *   - 응답 data.imageUrl 반환
     */
    private suspend fun uploadChatImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody =
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)

            val part = MultipartBody.Part.createFormData(
                name = "image",
                filename = "chat_${System.currentTimeMillis()}.jpg",
                body = requestBody
            )

            // 여기서 오류가 났었음 → ApiService 에 정의되어 있어야 함!
            val call = RetrofitClient.getApiService().uploadChatImage(roomId, part)
            val response = call.execute()

            if (!response.isSuccessful || response.body() == null) {
                Log.e("CHAT_UPLOAD_IMG", "서버 응답 실패: ${response.code()}")
                return@withContext null
            }

            val body = response.body()!!

            // MsgEntity.data 가 Map<String, Any> 형태일 때의 정석 파싱
            val data = body.data as? Map<*, *>
            val imageUrl = data?.get("imageUrl") as? String

            Log.d("CHAT_UPLOAD_IMG", "업로드 결과 URL: $imageUrl")
            return@withContext imageUrl

        } catch (e: Exception) {
            Log.e("CHAT_UPLOAD_IMG", "이미지 업로드 오류", e)
            return@withContext null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
            Log.d("STOMP_WS", "WebSocket 종료")
        }
    }

    private fun openRentRequestForm() {
        val id = productId
        if (id == null || id <= 0) {
            Toast.makeText(this, "상품 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, RentRequestActivity::class.java).apply {
            putExtra("ITEM_ID", id)
            putExtra("TITLE", productTitle ?: "")
            putExtra("PRICE", productPrice)
            putExtra("DEPOSIT", productDeposit)
            if (lenderId > 0) {
                putExtra("LENDER_ID", lenderId)
            }
            putExtra("SELLER_NICKNAME", intent.getStringExtra("SELLER_NICKNAME"))
        }
        startActivity(intent)
    }

    private fun confirmRental(payload: RentalActionPayload) {
        RetrofitClient.getApiService()
            .acceptRental(RentalDecisionRequest(payload.transactionId))
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        val summary = "대여가 확정되었습니다. 기간: ${payload.startDate} ~ ${payload.endDate}"
                        sendMessage(summary)
                        Toast.makeText(this@ChatRoomActivity, "대여가 확정되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChatRoomActivity, "대여 확정에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@ChatRoomActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
