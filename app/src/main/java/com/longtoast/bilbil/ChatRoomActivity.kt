package com.longtoast.bilbil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
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
import com.longtoast.bilbil.dto.RentalApproveRequest
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
        setSupportActionBar(toolbar)

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        // 🔹 Toolbar 내부 뷰들
        val titleText = findViewById<TextView>(R.id.text_chat_title)
        val rentAgreeButton = findViewById<Button>(R.id.btn_rent_agree)

        titleText.text = intent.getStringExtra("SELLER_NICKNAME") ?: "채팅"

        rentAgreeButton.setOnClickListener {
            openRentRequestForm()
        }

        chatAdapter = ChatAdapter(chatMessages, senderId.toString()) { payload ->
            confirmRental(payload)
        }
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        fetchChatHistory()
        connectWebSocket()
        setupListeners()
    }

    /** 🔵 RentRequestActivity 로 이동 */
    private fun openRentRequestForm() {
        val id = productId
        if (id == null || id <= 0) {
            Toast.makeText(this, "상품 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, RentRequestActivity::class.java).apply {
            putExtra("ITEM_ID", id)
            putExtra("BORROWER_ID", senderId)  // ⭐ 현재 사용자 = 대여자
            putExtra("LENDER_ID", lenderId)    // 기존 판매자 정보
        }
        Log.d("대여자ChatRoomActivity", "Lender ID: $lenderId")
        Log.d("차입자ChatRoomActivity", "Borrower ID: $senderId")
        startActivity(intent)
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

    /** 🔵 과거 채팅 불러오기 */
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

                        } catch (e: Exception) {
                            Log.e("CHAT_HISTORY", "파싱 오류", e)
                        }
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
        })
    }

    /** 🔵 STOMP 메시지 처리 */
    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                webSocket.send(
                    "SUBSCRIBE\nid:sub-0\ndestination:/topic/signal/$roomId\n\n\u0000"
                )
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

            var imageUrl: String? = null

            if (imageUri != null) {
                imageUrl = uploadChatImage(imageUri)
            }

            if (content.isEmpty() && imageUrl == null) return@launch

            val escapedContent = content.replace("\\", "\\\\")   // 역슬래시 먼저
                .replace("\"", "\\\"")   // 따옴표
                .replace("\n", "\\n")    // 🔥 개행 escape 추가!!!
            val payloadJson = buildString {
                append("{\"senderId\":$senderId")
                if (escapedContent.isNotEmpty()) append(",\"content\":\"$escapedContent\"")
                if (imageUrl != null) append(",\"imageUrl\":\"$imageUrl\"")
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
                "image",
                "chat_${System.currentTimeMillis()}.jpg",
                body
            )

            val response = RetrofitClient.getApiService().uploadChatImage(roomId, part).execute()
            if (!response.isSuccessful || response.body() == null) return@withContext null

            val data = response.body()!!.data as? Map<*, *>
            return@withContext data?.get("imageUrl") as? String

        } catch (e: Exception) {
            Log.e("UPLOAD_IMG", "ERROR", e)
            return@withContext null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
        }
    }

    /** 🔥 대여 합의 ‘동의하기’ */
    private fun confirmRental(payload: RentalActionPayload) {

        val request = RentalApproveRequest(
            roomId = payload.roomId,
            itemId = payload.itemId,
            lenderId = payload.lenderId,
            borrowerId = payload.borrowerId,
            startDate = payload.startDate,
            endDate = payload.endDate,
            totalAmount = payload.totalAmount
        )

        RetrofitClient.getApiService()
            .approveRental(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {

                        sendMessage(
                            "📌 대여가 확정되었습니다!\n" +
                                    "기간: ${payload.startDate} ~ ${payload.endDate}\n" +
                                    "총 금액: ${payload.totalAmount}원"
                        )

                        Toast.makeText(
                            this@ChatRoomActivity,
                            "대여 확정 완료",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {
                        Toast.makeText(
                            this@ChatRoomActivity,
                            "대여 확정 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(
                        this@ChatRoomActivity,
                        "네트워크 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}
