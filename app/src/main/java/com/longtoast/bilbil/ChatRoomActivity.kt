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

    // Intent 기반
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
    private val productPriceUnit: Int by lazy { intent.getIntExtra("PRICE_UNIT", 1) }

    private val productImageUrl: String? by lazy { intent.getStringExtra("IMAGE_URL") }
    private val sellerNickname: String? by lazy { intent.getStringExtra("SELLER_NICKNAME") }

    private val intentLenderId: Int by lazy { intent.getIntExtra("LENDER_ID", -1) }
    private val intentBorrowerId: Int by lazy { intent.getIntExtra("BORROWER_ID", -1) }

    // 역할 플래그 + 상대방 ID
    private var isLender: Boolean = false
    private var otherUserId: Int = -1

    private var nextTempId = -1L

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                sendMessage(editMessage.text.toString().trim(), it)
                editMessage.text.clear()
            }
        }

    // ======================================================================================
    //  onCreate
    // ======================================================================================
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

        val titleText = findViewById<TextView>(R.id.text_chat_title)
        val rentAgreeBtn = findViewById<Button>(R.id.btn_rent_agree)

        // UI 세팅
        titleText.text = sellerNickname ?: "채팅"

        rentAgreeBtn.setOnClickListener { openRentRequestForm() }

        chatAdapter = ChatAdapter(chatMessages, senderId.toString()) { payload ->
            confirmRental(payload)
        }
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        loadChatRoomRoleInfo()
        fetchChatHistory()
        connectWebSocket()
        setupListeners()
    }

    // ======================================================================================
    //  역할 로딩
    // ======================================================================================
    private fun loadChatRoomRoleInfo() {

        if (intentLenderId > 0 && intentBorrowerId > 0) {
            isLender = (senderId == intentLenderId)
            otherUserId = if (isLender) intentBorrowerId else intentLenderId
        }

        RetrofitClient.getApiService().getChatRoomInfo(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val raw = response.body()?.data ?: return
                    val map = raw as Map<String, Any>

                    val lenderId = (map["lenderId"] as Number).toInt()
                    val borrowerId = (map["borrowerId"] as Number).toInt()

                    isLender = (senderId == lenderId)
                    otherUserId = if (isLender) borrowerId else lenderId
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {}
            })
    }

    // ======================================================================================
    //  대여 요청 폼 이동 — 여기 수정됨 ⭐⭐⭐
    // ======================================================================================
    private fun openRentRequestForm() {

        val id = productId
        if (id == null || id <= 0) {
            Toast.makeText(this, "상품 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val realLenderId = if (isLender) senderId else otherUserId
        val realBorrowerId = if (isLender) otherUserId else senderId

        val intent = Intent(this, RentRequestActivity::class.java).apply {
            putExtra("ITEM_ID", id)
            putExtra("LENDER_ID", realLenderId)
            putExtra("BORROWER_ID", realBorrowerId)

            // ⭐⭐⭐ 추가된 부분 — 제목/가격/단위/보증금/이미지 전달
            putExtra("TITLE", productTitle)
            putExtra("PRICE", productPrice)
            putExtra("PRICE_UNIT", productPriceUnit)
            putExtra("DEPOSIT", productDeposit)
            putExtra("IMAGE_URL", productImageUrl)
        }

        Log.d(
            "OPEN_FORM",
            "item=$id / lender=$realLenderId / borrower=$realBorrowerId / title=$productTitle / img=$productImageUrl"
        )

        startActivity(intent)
    }

    // ======================================================================================
    //  listeners
    // ======================================================================================
    private fun setupListeners() {
        buttonSend.setOnClickListener {
            val text = editMessage.text.toString().trim()
            if (text.isNotEmpty() || selectedImageUri != null) {
                sendMessage(text, selectedImageUri)
                editMessage.text.clear()
            }
        }

        buttonAttachImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ======================================================================================
    //  채팅 기록
    // ======================================================================================
    private fun fetchChatHistory() {
        RetrofitClient.getApiService().getChatHistory(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {

                    val raw = response.body()?.data ?: return
                    val gson = Gson()

                    val listType = object : TypeToken<List<ChatMessage>>() {}.type
                    val list: List<ChatMessage> = gson.fromJson(gson.toJson(raw), listType)

                    chatMessages.addAll(list)
                    chatAdapter.notifyDataSetChanged()

                    if (chatMessages.isNotEmpty()) {
                        recyclerChat.scrollToPosition(chatMessages.size - 1)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_HISTORY", "error", t)
                }
            })
    }

    // ======================================================================================
    //  STOMP 웹소켓
    // ======================================================================================
    private fun connectWebSocket() {
        val token = AuthTokenManager.getToken()

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                val connectFrame =
                    "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer $token\n\n\u0000"
                ws.send(connectFrame)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runOnUiThread { handleStompFrame(text) }
            }
        })
    }

    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                webSocket.send(
                    "SUBSCRIBE\nid:sub-0\ndestination:/topic/signal/$roomId\n\n\u0000"
                )
            }

            frame.startsWith("MESSAGE") -> {
                val parts = frame.split("\n\n")
                if (parts.size <= 1) return

                val payload = parts[1].replace("\u0000", "")

                try {
                    val received = Gson().fromJson(payload, ChatMessage::class.java)

                    if (received.senderId == senderId) {
                        val match = tempMessageMap.entries.firstOrNull {
                            it.value.content == received.content &&
                                    it.value.imageUrl == received.imageUrl
                        }

                        if (match != null) {
                            val index = chatMessages.indexOf(match.value)
                            if (index != -1) {
                                chatMessages[index] = received
                                chatAdapter.notifyItemChanged(index)
                            }
                            tempMessageMap.remove(match.key)
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

    // ======================================================================================
    //  메시지 전송
    // ======================================================================================
    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {

            var imageUrl: String? = null

            if (imageUri != null) {
                imageUrl = uploadChatImage(imageUri)
            }

            if (content.isEmpty() && imageUrl == null) return@launch

            val escaped = content.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

            val payloadJson = buildString {
                append("{\"senderId\":$senderId")
                if (escaped.isNotEmpty()) append(",\"content\":\"$escaped\"")
                if (imageUrl != null) append(",\"imageUrl\":\"$imageUrl\"")
                append("}")
            }

            val messageFrame =
                "SEND\ndestination:/app/signal/$roomId\ncontent-type:application/json\n\n$payloadJson\u0000"

            webSocket.send(messageFrame)

            // 로컬 에코 메시지
            val tempMsg = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = if (content.isNotEmpty()) content else null,
                imageUrl = imageUrl,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(
                    Date()
                )
            )

            chatMessages.add(tempMsg)
            tempMessageMap[tempMsg.id] = tempMsg
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recyclerChat.scrollToPosition(chatMessages.size - 1)

            selectedImageUri = null
        }
    }

    // ======================================================================================
    //  이미지 업로드
    // ======================================================================================
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

            val resp = RetrofitClient.getApiService()
                .uploadChatImage(roomId, part)
                .execute()

            if (!resp.isSuccessful) return@withContext null

            val data = resp.body()?.data as? Map<*, *> ?: return@withContext null
            return@withContext data["imageUrl"] as? String

        } catch (e: Exception) {
            Log.e("UPLOAD_IMG", "ERROR", e)
            return@withContext null
        }
    }

    // ======================================================================================
    //  대여 확정 처리
    // ======================================================================================
    private fun confirmRental(payload: RentalActionPayload) {

        val realLenderId = if (isLender) senderId else otherUserId
        val realBorrowerId = if (isLender) otherUserId else senderId

        val req = RentalApproveRequest(
            roomId = payload.roomId,
            itemId = payload.itemId,
            lenderId = realLenderId,
            borrowerId = realBorrowerId,
            startDate = payload.startDate,
            endDate = payload.endDate,
            totalAmount = payload.totalAmount
        )

        RetrofitClient.getApiService().approveRental(req)
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

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
        }
    }
}
