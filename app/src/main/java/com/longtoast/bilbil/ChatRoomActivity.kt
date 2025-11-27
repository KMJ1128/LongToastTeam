package com.longtoast.bilbil

import android.content.Intent
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
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>()

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL

    // ✅ FIX: ROOM_ID를 먼저 String으로 읽고, 안 되면 Int로 읽기
    private val roomId: Int by lazy {
        val fromString = intent.getStringExtra("ROOM_ID")?.toIntOrNull()
        fromString ?: intent.getIntExtra("ROOM_ID", -1)
    }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

    private val productId: Int? by lazy {
        val numeric = intent.getIntExtra("PRODUCT_ID", -1)
        if (numeric > 0) numeric else intent.getStringExtra("PRODUCT_ID")?.toIntOrNull()
    }
    private val productTitle: String? by lazy { intent.getStringExtra("PRODUCT_TITLE") }
    private val productPrice: Int by lazy { intent.getIntExtra("PRODUCT_PRICE", 0) }
    private val productDeposit: Int by lazy { intent.getIntExtra("PRODUCT_DEPOSIT", 0) }
    private val lenderId: Int by lazy { intent.getIntExtra("LENDER_ID", -1) }

    private var nextTempId = -1L

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

        if (roomId <= 0) {
            Toast.makeText(this, "채팅방 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

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

    /** 🔵 대여 요청 폼 열기 (병합 완성본) */
    private fun openRentRequestForm() {
        val id = productId
        if (id == null || id <= 0) {
            Toast.makeText(this, "상품 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val chatIntent = intent
        val intent = Intent(this, RentRequestActivity::class.java).apply {
            putExtra("ITEM_ID", id)
            putExtra("TITLE", productTitle)
            putExtra("PRICE", productPrice)
            putExtra("DEPOSIT", productDeposit)
            putExtra("LENDER_ID", lenderId)
            putExtra("SELLER_NICKNAME", chatIntent.getStringExtra("SELLER_NICKNAME"))
        }
        startActivity(intent)
    }

    private fun setupToolbar() {
        toolbar.title = intent.getStringExtra("SELLER_NICKNAME") ?: "채팅"
        toolbar.inflateMenu(R.menu.menu_chat_room)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_rent_request) {
                openRentRequestForm()
                true
            } else false
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

    /** 🔵 채팅방 히스토리 불러오기 */
    private fun fetchChatHistory() {
        if (roomId <= 0) {
            Toast.makeText(this, "채팅방 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

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

                        } catch (e: Exception) {
                            Log.e("CHAT_HISTORY", "파싱 오류", e)
                        }
                    } else {
                        Log.e("CHAT_HISTORY", "내역 조회 실패: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_HISTORY", "네트워크 오류", t)
                }
            })
    }

    /** 🔵 WebSocket 연결 */
    private fun connectWebSocket() {
        val token = AuthTokenManager.getToken()
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(WEBSOCKET_URL)
        if (token != null) requestBuilder.addHeader("Authorization", "Bearer $token")

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                val connectFrame =
                    "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer $token\n\n\u0000"
                webSocket.send(connectFrame)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread { handleStompFrame(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Toast.makeText(this@ChatRoomActivity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
        })
    }

    /** 🔵 STOMP 프레임 처리 */
    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                val subscribeFrame =
                    "SUBSCRIBE\nid:sub-0\ndestination:/topic/signal/$roomId\n\n\u0000"
                webSocket.send(subscribeFrame)
            }

            frame.startsWith("MESSAGE") -> {
                val parts = frame.split("\n\n")
                if (parts.size > 1) {
                    val payload = parts[1].replace("\u0000", "")

                    try {
                        val gson = Gson()
                        val received = gson.fromJson(payload, ChatMessage::class.java)

                        if (received.senderId == senderId) {
                            val matchEntry = tempMessageMap.entries.firstOrNull {
                                it.value.content == received.content &&
                                        it.value.imageUrl == received.imageUrl
                            }

                            if (matchEntry != null) {
                                val index = chatMessages.indexOf(matchEntry.value)
                                if (index != -1) {
                                    chatMessages[index] = received
                                    chatAdapter.notifyItemChanged(index)
                                }
                                tempMessageMap.remove(matchEntry.key)
                            } else {
                                chatMessages.add(received)
                                chatAdapter.notifyItemInserted(chatMessages.size - 1)
                            }
                        } else {
                            chatMessages.add(received)
                            chatAdapter.notifyItemInserted(chatMessages.size - 1)
                        }

                        recyclerChat.scrollToPosition(chatMessages.size - 1)

                    } catch (e: Exception) {
                        Log.e("STOMP_MSG", "파싱 오류", e)
                    }
                }
            }
        }
    }

    /** 🔵 메시지 전송 */
    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {
            val finalImageUri = imageUri ?: selectedImageUri
            var imageUrl: String? = null

            if (finalImageUri != null) {
                imageUrl = uploadChatImage(finalImageUri)
                if (imageUrl == null) {
                    Toast.makeText(this@ChatRoomActivity, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                }
            }

            if (content.isEmpty() && imageUrl.isNullOrEmpty()) return@launch

            val escapedContent = content.replace("\"", "\\\"")
            val payloadJson = buildString {
                append("{\"senderId\":$senderId")
                if (escapedContent.isNotEmpty()) append(",\"content\":\"$escapedContent\"")
                if (!imageUrl.isNullOrEmpty()) append(",\"imageUrl\":\"$imageUrl\"")
                append("}")
            }

            val messageFrame =
                "SEND\ndestination:/app/signal/$roomId\ncontent-type:application/json\n\n$payloadJson\u0000"

            webSocket.send(messageFrame)

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

            selectedImageUri = null
        }
    }

    /** 🔵 이미지 업로드 */
    private suspend fun uploadChatImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes()
            stream.close()

            val body = RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)
            val part = MultipartBody.Part.createFormData(
                name = "image",
                filename = "chat_${System.currentTimeMillis()}.jpg",
                body = body
            )

            val response = RetrofitClient.getApiService()
                .uploadChatImage(roomId, part)
                .execute()

            if (!response.isSuccessful || response.body() == null) return@withContext null

            val data = response.body()!!.data as? Map<*, *>
            return@withContext data?.get("imageUrl") as? String

        } catch (e: Exception) {
            Log.e("UPLOAD_IMG", "오류", e)
            return@withContext null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
        }
    }

    /** 🔵 대여 확정 처리 */
    private fun confirmRental(payload: RentalActionPayload) {
        RetrofitClient.getApiService()
            .acceptRental(RentalDecisionRequest(payload.transactionId))
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        sendMessage("대여가 확정되었습니다. 기간: ${payload.startDate} ~ ${payload.endDate}")
                        Toast.makeText(this@ChatRoomActivity, "대여 확정 완료", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ChatRoomActivity, "대여 확정 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@ChatRoomActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
