package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.ActivityChatRoomBinding
import com.longtoast.bilbil.dto.ChatMessage

// import com.longtoast.bilbil.adapter.ChatAdapter // TODO: 3단계에서 생성할 어댑터
// import com.longtoast.bilbil.dto.ChatMessage // TODO: 3단계에서 생성할 데이터 모델

class ChatRoomActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatRoomBinding
    // private lateinit var chatAdapter: ChatAdapter // TODO: 어댑터
    private val chatMessages = mutableListOf<ChatMessage>() // TODO: 메시지 목록

    private var roomId: String? = null // 🚨 추가: 채팅방 ID 저장 필드
    private var productId: String? = null
    private var sellerNickname: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Intent에서 데이터 가져오기
        roomId = intent.getStringExtra("ROOM_ID") // 🚨 ROOM_ID 가져오기
        productId = intent.getStringExtra("PRODUCT_ID")
        sellerNickname = intent.getStringExtra("SELLER_NICKNAME") ?: "대화 상대"

        // 🚨 필수 정보 검증: roomId 또는 productId가 없으면 종료
        if (productId == null || roomId == null) {
            Toast.makeText(this, "필수 정보(상품/채팅방 ID)가 없습니다. 채팅방을 종료합니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        Log.d("CHAT_ROOM", "채팅방 진입 성공. Room ID: $roomId")

        // 2. 툴바 설정
        setupToolbar()

        // 3. RecyclerView 설정
        setupRecyclerView()

        // 4. 전송 버튼 리스너 설정
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }

        // 5. (TODO) 서버에서 이전 대화 내역 불러오기 (ROOM_ID 사용)
        loadChatHistory(roomId!!) // 🚨 roomId 사용하도록 수정
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarChat)
        supportActionBar?.title = sellerNickname
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 뒤로가기 버튼 활성화
    }

    private fun setupRecyclerView() {
        // TODO: 3단계 - ChatAdapter 생성 및 연결
        // chatAdapter = ChatAdapter(chatMessages, /* 현재 유저 ID */)
        binding.recyclerViewChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 메시지 목록을 아래부터 쌓음
        }
        // binding.recyclerViewChat.adapter = chatAdapter
    }

    private fun loadChatHistory(roomId: String) {
        // TODO: 4단계 - Retrofit 또는 WebSocket을 사용하여 서버에서 이 roomId에 해당하는
        // 채팅 내역을 불러와서 chatMessages 리스트에 추가합니다.
        Log.d("CHAT_HISTORY", "Room ID $roomId 의 이전 대화 내역 로드 시작...")
    }

    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty()) {
            // TODO: 5단계 - 메시지 전송 로직 (WebSocket)
            Log.d("CHAT_SEND", "Room ID $roomId 로 메시지 전송: $messageText")

            // 1. ChatMessage 객체 생성 (임시)
            // val newMessage = ChatMessage(
            //     id = 0, // 임시 ID
            //     roomId = roomId!!,
            //     senderId = "MY_ID", // 실제 현재 사용자 ID
            //     content = messageText,
            //     imageUrl = null,
            //     sentAt = LocalDateTime.now().toString()
            // )

            // 2. (TODO) 서버로 메시지 전송 (WebSocket SEND)
            // stompClient.send("/app/signal/$roomId", convertToJson(newMessage))

            // 3. UI에 즉시 반영
            // chatMessages.add(newMessage)
            // chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.recyclerViewChat.scrollToPosition(chatMessages.size - 1) // 맨 아래로 스크롤

            // 4. 입력창 비우기
            binding.editTextMessage.text.clear()
        }
    }

    // 툴바의 뒤로가기 버튼 클릭 처리
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish() // 현재 액티비티 종료
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}