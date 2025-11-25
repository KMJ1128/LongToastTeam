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
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>() // 로컬 임시 메시지 캐시

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private val roomId by lazy { intent.getStringExtra("ROOM_ID") ?: "1" }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

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

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        partnerNickname = intent.getStringExtra("PARTNER_NICKNAME")
            ?: intent.getStringExtra("SELLER_NICKNAME")
        partnerProfileImageUrl = intent.getStringExtra("PARTNER_PROFILE")

        // 🔥 senderId → String X (Crash 원인)
        chatAdapter = ChatAdapter(chatMessages, senderId, partnerNickname, partnerProfileImageUrl)

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
                override fun onResponse(call: retrofit2.Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("CHAT_HISTORY", "❌ 실패 코드: ${response.code()}")
                        return
                    }

                    try {
                        val listType = object : TypeToken<List<ChatMessage>>() {}.type
                        val history: List<ChatMessage> =
                            Gson().fromJson(Gson().toJson(response.body()!!.data), listType)

                        chatMessages.clear()
                        chatMessages.addAll(history)
                        chatAdapter.submitMessages(chatMessages)

                        recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)

                    } catch (e: Exception) {
                        Log.e("CHAT_HISTORY", "파싱 오류", e)
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

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(ws: WebSocket, response: okhttp3.Response) {
                    val connectFrame = "CONNECT\n" +
                            "accept-version:1.2\n" +
                            "heart-beat:10000,10000\n" +
                            "Authorization:Bearer $token\n\n\u0000"

                    ws.send(wrapSockJsFrame(connectFrame))
                }

                override fun onMessage(ws: WebSocket, raw: String) {
                    runOnUiThread { handleSockJsFrame(raw) }
                }
            }
        )
    }

    private fun handleSockJsFrame(raw: String) {
        if (raw == "o" || raw == "h") return

        if (raw.startsWith("a[")) {
            val frames: List<String> =
                Gson().fromJson(raw.substring(1), object : TypeToken<List<String>>() {}.type)

            frames.forEach { handleStompFrame(it) }
            return
        }

        handleStompFrame(raw)
    }

    private fun handleStompFrame(frame: String) {
        when {
            frame.startsWith("CONNECTED") -> {
                webSocket.send(
                    wrapSockJsFrame(
                        "SUBSCRIBE\nid:sub-0\ndestination:/topic/signal/$roomId\n\n\u0000"
                    )
                )
            }

            frame.startsWith("MESSAGE") -> {
                val payload = frame.substringAfter("\n\n").replace("\u0000", "")

                try {
                    val msg = Gson().fromJson(payload, ChatMessage::class.java)

                    if (msg.senderId == senderId) {
                        val match = tempMessageMap.entries.firstOrNull {
                            it.value.content == msg.content ||
                                    (!it.value.imageUrl.isNullOrBlank() && it.value.imageUrl == msg.imageUrl)
                        }

                        if (match != null) {
                            val idx = chatMessages.indexOf(match.value)
                            if (idx != -1) chatMessages[idx] = msg
                            tempMessageMap.remove(match.key)
                        } else {
                            chatMessages.add(msg)
                        }
                    } else {
                        chatMessages.add(msg)
                    }

                    chatAdapter.submitMessages(chatMessages)
                    recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)

                } catch (e: Exception) {
                    Log.e("STOMP", "JSON 오류", e)
                }
            }
        }
    }

    private fun wrapSockJsFrame(frame: String): String {
        val escaped = frame
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\u0000", "\\u0000")

        return "[\"$escaped\"]"
    }

    private fun sendMessage(content: String, imageUri: Uri? = null) {
        lifecycleScope.launch {
            val finalUri = imageUri ?: selectedImageUri
            var uploadedUrl: String? = null

            if (finalUri != null) {
                uploadedUrl = uploadImageForChat(finalUri)
                if (uploadedUrl.isNullOrEmpty()) {
                    Toast.makeText(this@ChatRoomActivity, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            val escaped = content.replace("\"", "\\\"")

            val json = buildString {
                append("{\"senderId\":$senderId")
                if (escaped.isNotEmpty()) append(",\"content\":\"$escaped\"")
                if (!uploadedUrl.isNullOrEmpty()) append(",\"imageUrl\":\"$uploadedUrl\"")
                append("}")
            }

            val frame = "SEND\n" +
                    "destination:/app/signal/$roomId\n" +
                    "content-type:application/json\n\n" +
                    json + "\u0000"

            // 🔥 충돌 해결: SockJS 버전만 사용
            webSocket.send(wrapSockJsFrame(frame))

            val temp = ChatMessage(
                id = nextTempId--,
                roomId = roomId,
                senderId = senderId,
                content = if (content.isNotEmpty()) content else null,
                imageUrl = uploadedUrl,
                sentAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )

            chatMessages.add(temp)
            tempMessageMap[temp.id] = temp

            chatAdapter.submitMessages(chatMessages)
            recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)

            selectedImageUri = null
        }
    }

    private suspend fun uploadImageForChat(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val part = ImageUtil.uriToMultipart(this@ChatRoomActivity, uri, "image")
                ?: return@withContext null

            val response = RetrofitClient.getApiService().uploadChatImage(roomId, part).execute()
            if (!response.isSuccessful) return@withContext null

            val raw = response.body()?.data ?: return@withContext null

            return@withContext when (raw) {
                is String -> raw
                else -> Gson().toJson(raw).trim('"')
            }

        } catch (e: Exception) {
            Log.e("UPLOAD", "오류", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
        }
    }
}
