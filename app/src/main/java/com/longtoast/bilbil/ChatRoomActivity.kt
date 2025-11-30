package com.longtoast.bilbil

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    private lateinit var handshakeAnimation: LottieAnimationView
    private lateinit var overlayBlock: View

    private var selectedImageUri: Uri? = null
    private val chatMessages = mutableListOf<ChatMessage>()
    private val tempMessageMap = mutableMapOf<Long, ChatMessage>()

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL

    private val roomId: Int by lazy {
        intent.getStringExtra("ROOM_ID")?.toIntOrNull()
            ?: intent.getIntExtra("ROOM_ID", -1)
    }

    private val senderId: Int by lazy { AuthTokenManager.getUserId() ?: 1 }

    private var productId: Int? = null
    private var productTitle: String? = null
    private var productPrice: Int = 0
    private var productDeposit: Int = 0
    private var productPriceUnit: Int = 1
    private var productImageUrl: String? = null

    private var isLender: Boolean = false
    private var otherUserId: Int = -1

    private var partnerNickname: String? = null
    private var partnerProfileImageUrl: String? = null

    private var nextTempId = -1L

    // 이미지 선택기
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
            if (lenderFromIntent > 0) isLender = senderId == lenderFromIntent

            otherUserId = when {
                isLender && borrowerFromIntent > 0 -> borrowerFromIntent
                !isLender && lenderFromIntent > 0 -> lenderFromIntent
                else -> otherUserId
            }
        }

        intent.getIntExtra("PARTNER_ID", -1)
            .takeIf { it > 0 && otherUserId <= 0 }
            ?.let { otherUserId = it }

        partnerNickname = intent.getStringExtra("SELLER_NICKNAME")
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar_chat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        recyclerChat = findViewById(R.id.recycler_view_chat)
        editMessage = findViewById(R.id.edit_text_message)
        buttonSend = findViewById(R.id.button_send)
        buttonAttachImage = findViewById(R.id.button_attach_image)

        handshakeAnimation = findViewById(R.id.handshake_animation)
        overlayBlock = findViewById(R.id.overlay_block)

        val partnerNameText = findViewById<TextView>(R.id.text_chat_partner_name)
        val partnerImage = findViewById<ImageView>(R.id.image_chat_partner)
        val rentAgreeBtn = findViewById<Button>(R.id.btn_rent_agree)
        val reportBtn = findViewById<Button>(R.id.btn_report)

        partnerNameText.text = partnerNickname ?: "채팅"
        partnerImage.setImageResource(R.drawable.no_profile)

        // 대여 합의하기
        rentAgreeBtn.setOnClickListener {
            openRentRequestForm()
        }
        rentAgreeBtn.bringToFront()

        // 🔥 신고 버튼
        reportBtn.setOnClickListener {
            showReportDialog()
        }
        reportBtn.bringToFront()
    }

    private fun setupRecycler() {
        chatAdapter = ChatAdapter(chatMessages, senderId.toString()) { payload ->
            confirmRental(payload)
        }
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        chatAdapter.setPartnerInfo(partnerNickname, partnerProfileImageUrl)
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

                    partnerNickname =
                        if (isLender) info.borrower.nickname else info.lender.nickname
                    partnerProfileImageUrl =
                        if (isLender) info.borrower.profileImageUrl else info.lender.profileImageUrl

                    val partnerNameText = findViewById<TextView>(R.id.text_chat_partner_name)
                    val profileImage = findViewById<ImageView>(R.id.image_chat_partner)

                    partnerNameText.text = partnerNickname ?: "채팅"

                    val fullProfile = ImageUrlUtils.resolve(partnerProfileImageUrl)
                    if (!fullProfile.isNullOrEmpty()) {
                        Glide.with(this@ChatRoomActivity)
                            .load(fullProfile)
                            .placeholder(R.drawable.no_profile)
                            .error(R.drawable.no_profile)
                            .circleCrop()
                            .into(profileImage)
                    } else profileImage.setImageResource(R.drawable.no_profile)

                    chatAdapter.setPartnerInfo(partnerNickname, partnerProfileImageUrl)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("ROOM_INFO", "error", t)
                }
            })
    }

    private fun openRentRequestForm() {

        val id = productId ?: return

        if (otherUserId <= 0) {
            Toast.makeText(this, "상대방 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
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

                    // ✅ 이 방이 예전에 대여 확정된 방이라면 버튼 비활성 상태 복원
                    restoreRentalConfirmedFlagIfNeeded()

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

                    // ---------------------------------------------------------
                    // 🔥 1) 읽음 이벤트 우선 처리
                    // ---------------------------------------------------------
                    if (received.isRead == true) {
                        val idx = chatMessages.indexOfFirst { it.id == received.id }
                        if (idx != -1) {
                            chatMessages[idx] = received
                            chatAdapter.notifyItemChanged(idx)
                        }
                        return  // 읽음 이벤트는 여기서 끝
                    }

                    // ---------------------------------------------------------
                    // 2) 대여 확정 애니메이션
                    // ---------------------------------------------------------
                    if (received.content?.contains("대여가 확정되었습니다") == true) {
                        playHandshakeAnimation()
                    }

                    // ---------------------------------------------------------
                    // 3) 일반 메시지 처리
                    // ---------------------------------------------------------
                    if (received.senderId == senderId) {

                        val match =
                            tempMessageMap.entries.firstOrNull {
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

                    // 📌 내가 현재 방에 있을 때 받은 메시지는 바로 읽음 처리 요청
                    if (received.senderId != senderId) {
                        markChatAsRead()
                    }

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
                ).format(Date()),
                isRead = false
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

                val originalBytes = stream.readBytes()
                stream.close()

                val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                val rotatedBitmap = applyExifRotation(uri, bitmap)

                val outputStream = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val compressedBytes = outputStream.toByteArray()

                val body = RequestBody.create("image/jpeg".toMediaTypeOrNull(), compressedBytes)
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

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        } catch (e: Exception) {
            bitmap
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

                        // ✅ 로컬에 이 방의 "대여 확정 완료" 상태 저장
                        saveRentalConfirmedFlag()

                        playHandshakeAnimation()

                        chatAdapter.markRentalConfirmed(payload)

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

    private fun playHandshakeAnimation() {
        overlayBlock.visibility = View.VISIBLE
        handshakeAnimation.visibility = View.VISIBLE
        handshakeAnimation.playAnimation()

        handshakeAnimation.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                handshakeAnimation.visibility = View.GONE
                overlayBlock.visibility = View.GONE
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }

    // 🔥 신고 다이얼로그
    private fun showReportDialog() {
        if (roomId <= 0) {
            Toast.makeText(this, "채팅방 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            hint = "신고 사유를 입력해주세요"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(4)
            gravity = Gravity.TOP or Gravity.START
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("신고하기")
            .setMessage("해당 채팅방을 신고하시겠습니까?\n구체적인 신고 사유를 적어주세요.")
            .setView(input)
            .setPositiveButton("신고") { dialog, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "신고 사유를 입력해주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    sendReport(reason)
                }
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // 🔥 신고 요청 전송
    private fun sendReport(reason: String) {
        val req = ChatRoomReportRequest(
            roomId = roomId,
            reason = reason
        )

        RetrofitClient.getApiService()
            .reportChatRoom(req)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@ChatRoomActivity,
                            "신고가 접수되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@ChatRoomActivity,
                            "신고 처리에 실패했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_REPORT", "신고 전송 실패", t)
                    Toast.makeText(
                        this@ChatRoomActivity,
                        "네트워크 오류로 신고에 실패했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // 🔥 채팅방 들어올 때 전체 메시지 읽음 처리
    override fun onResume() {
        super.onResume()
        markChatAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Activity destroyed")
        }
    }

    // 서버에 읽음 처리 요청을 보내고, 결과는 STOMP 이벤트로 받아 UI를 갱신한다.
    private fun markChatAsRead() {
        RetrofitClient.getApiService()
            .markChatRead(roomId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    // 서버가 STOMP로 읽음 이벤트 브로드캐스트 → handleStompFrame()에서 처리됨
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_READ", "읽음 처리 실패", t)
                }
            })
    }

    // 🔥 이 방에서 대여가 한 번 확정되면 true 로 저장
    private fun saveRentalConfirmedFlag() {
        val prefs = getSharedPreferences("rental_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("rental_confirmed_room_$roomId", true)
            .apply()
    }

    // 🔥 채팅방 다시 들어왔을 때(혹은 히스토리 로딩 후) 저장된 값 복원
    private fun restoreRentalConfirmedFlagIfNeeded() {
        val prefs = getSharedPreferences("rental_prefs", MODE_PRIVATE)
        val confirmed = prefs.getBoolean("rental_confirmed_room_$roomId", false)
        if (confirmed) {
            // 어댑터에게 "이미 확정된 방이다" 알려주기
            chatAdapter.restoreRentalConfirmedFromStorage()
        }
    }
}
