package com.longtoast.bilbil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
        intent.getStringExtra("ROOM_ID")?.toIntOrNull()
            ?: intent.getIntExtra("ROOM_ID", -1)
    }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

    // 서버에서 받아오는 값들
    private var productId: Int? = null
    private var productTitle: String? = null
    private var productPrice: Int = 0
    private var productDeposit: Int = 0
    private var productPriceUnit: Int = 1
    private var productImageUrl: String? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_room)

        if (roomId <= 0) {
            Toast.makeText(this, "채팅방 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        preloadIntentData()
        setupViews()
        setupRecycler()

        loadChatRoomRoleInfo()
        fetchChatHistory()
        connectWebSocket()
        setupListeners()
    }

    private fun preloadIntentData() {
        intent.getIntExtra("ITEM_ID", -1).takeIf { it > 0 }?.let { productId = it }
        productTitle = intent.getStringExtra("PRODUCT_TITLE") ?: productTitle
        productPrice = intent.getIntExtra("PRODUCT_PRICE", productPrice)
        productDeposit = intent.getIntExtra("PRODUCT_DEPOSIT", productDeposit)
        productPriceUnit = intent.getIntExtra("PRICE_UNIT", productPriceUnit)
        productImageUrl = intent.getStringExtra("IMAGE_URL") ?: productImageUrl

        val lenderFromIntent = intent.getIntExtra("LENDER_ID", -1)
        val borrowerFromIntent = intent.getIntExtra("BORROWER_ID", -1)

        if (lenderFromIntent > 0 || borrowerFromIntent > 0) {
            if (lenderFromIntent > 0) {
                isLender = senderId == lenderFromIntent
            }
            otherUserId = when {
                isLender && borrowerFromIntent > 0 -> borrowerFromIntent
                !isLender && lenderFromIntent > 0 -> lenderFromIntent
                else -> otherUserId
            }
        }

        intent.getIntExtra("PARTNER_ID", -1)
            .takeIf { it > 0 && otherUserId <= 0 }
            ?.let { otherUserId = it }
    }


    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar_chat)
        setSupportActionBar(toolbar)

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        val titleText = findViewById<TextView>(R.id.text_chat_title)
        val rentAgreeBtn = findViewById<Button>(R.id.btn_rent_agree)

        titleText.text = intent.getStringExtra("SELLER_NICKNAME") ?: "채팅"

        rentAgreeBtn.setOnClickListener {
            Log.d("RENT_BTN", "대여 합의하기 버튼 클릭됨!!!")
            Toast.makeText(this, "대여 합의하기 버튼 클릭됨", Toast.LENGTH_SHORT).show()
            openRentRequestForm()
        }

        rentAgreeBtn.bringToFront()
    }


    private fun setupRecycler() {
        chatAdapter = ChatAdapter(chatMessages, senderId.toString()) { payload ->
            confirmRental(payload)
        }
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)
    }


    private fun loadChatRoomRoleInfo() {

        RetrofitClient.getApiService().getChatRoomInfo(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {

                    val infoJson = Gson().toJson(response.body()?.data)
                    val info = Gson().fromJson(infoJson, ChatRoomInfoResponse::class.java)

                    val item = info.item
                    productId = item.id
                    productTitle = item.title
                    productPrice = item.price
                    productImageUrl = item.imageUrl

                    isLender = (senderId == info.lender.id)
                    otherUserId = if (isLender) info.borrower.id else info.lender.id

                    Log.d("ROOM_INFO_ITEM", "itemId=$productId / title=$productTitle / price=$productPrice / img=$productImageUrl")
                    Log.d("ROOM_INFO_ROLE", "isLender=$isLender senderId=$senderId lender=${info.lender.id} borrower=${info.borrower.id}")
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("ROOM_INFO", "error", t)
                }
            })
    }


    private fun openRentRequestForm() {

        val id = productId

        if (id == null || id <= 0) {
            Toast.makeText(this, "상품 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT)
                .show()
            loadChatRoomRoleInfo()
            return
        }

        if (otherUserId <= 0) {
            Toast.makeText(this, "상대방 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            loadChatRoomRoleInfo()
            return
        }

        val raw = productImageUrl ?: return

        val fullImageUrl = if (raw.startsWith("http")) raw else {
            val base = ServerConfig.IMG_BASE_URL.trimEnd('/')
            val path = raw.trimStart('/')
            "$base/$path"
        }
        val realLenderId = if (isLender) senderId else otherUserId
        val realBorrowerId = if (isLender) otherUserId else senderId

        val intent = Intent(this, RentRequestActivity::class.java).apply {
            putExtra("ITEM_ID", id)
            putExtra("LENDER_ID", realLenderId)
            putExtra("BORROWER_ID", realBorrowerId)

            putExtra("TITLE", productTitle)
            putExtra("PRICE", productPrice)
            putExtra("PRICE_UNIT", productPriceUnit)
            putExtra("DEPOSIT", productDeposit)
            putExtra("IMAGE_URL", fullImageUrl)
        }

        Log.d("OPEN_FORM",
            "item=$id lender=$realLenderId borrower=$realBorrowerId img=$productImageUrl"
        )

        startActivity(intent)
    }


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


    private fun connectWebSocket() {

        val token = AuthTokenManager.getToken()

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                val frame =
                    "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer $token\n\n\u0000"
                ws.send(frame)
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
                            val idx = chatMessages.indexOf(match.value)
                            if (idx != -1) chatMessages[idx] = received
                            chatAdapter.notifyItemChanged(idx)
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

            val frame =
                "SEND\ndestination:/app/signal/$roomId\ncontent-type:application/json\n\n$payloadJson\u0000"

            webSocket.send(frame)

            val tempMsg = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = if (content.isNotEmpty()) content else null,
                imageUrl = imageUrl,
                sentAt = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())
            )

            chatMessages.add(tempMsg)
            tempMessageMap[tempMsg.id] = tempMsg
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recyclerChat.scrollToPosition(chatMessages.size - 1)

            selectedImageUri = null
        }
    }


    private suspend fun uploadChatImage(uri: Uri): String? =
        withContext(Dispatchers.IO) {
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


    private fun confirmRental(payload: RentalActionPayload) {

        val req = RentalApproveRequest(
            roomId = payload.roomId,
            itemId = payload.itemId,
            lenderId = if (isLender) senderId else otherUserId,
            borrowerId = if (isLender) otherUserId else senderId,
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
