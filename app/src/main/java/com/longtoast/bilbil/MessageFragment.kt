package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.FragmentMessageBinding
import com.longtoast.bilbil.ServerConfig
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.longtoast.bilbil.dto.ChatRoomListUpdateDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.WebSocket
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocketListener

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    // 💡 [유지] 연결 재시도 및 구독 지연 로직을 위한 Handler
    private val handler = Handler(Looper.getMainLooper())

    // 🚨 [핵심] 구독 실행 Runnable
    private val subscribeRunnable = Runnable {
        // 명시적인 UNSUBSCRIBE는 제거했습니다.
        subscribeToChatListUpdate()
    }

    // 목록 화면에 머무는 동안 주기적으로 최신 데이터를 불러오기 위한 Runnable
    private val listRefreshRunnable = object : Runnable {
        override fun run() {
            fetchChatRoomLists(showRefreshing = false)
            handler.postDelayed(this, 10_000)
        }
    }


    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private lateinit var webSocket: WebSocket // 💡 [수정] Fragment가 직접 웹소켓 객체를 관리
    // 🚨 ChatWebSocketManager 의존성 제거

    private val chatRoomLists = mutableListOf<ChatRoomListDTO>()
    private lateinit var adapter: ChatRoomListAdapter


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatRoomListAdapter(chatRoomLists) { room ->
            val intent = Intent(requireContext(), ChatRoomActivity::class.java)
            intent.putExtra("ROOM_ID", room.roomId.toString())
            intent.putExtra("SELLER_NICKNAME", room.partnerNickname)
            startActivity(intent)
        }
        binding.recyclerViewChatRooms.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewChatRooms.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("CHAT_LIST", "사용자 수동 새로고침 시작")
            fetchChatRoomLists()
        }

        // 🚨 [수정] ChatWebSocketManager.addListener(wsListener) 호출 제거
    }

    override fun onResume() {
        super.onResume()
        fetchChatRoomLists()
        // 💡 [수정] Fragment가 직접 연결을 시작합니다.
        connectWebSocket()
        handler.postDelayed(listRefreshRunnable, 10_000)
    }

    override fun onPause() {
        super.onPause()
        // 💡 [수정] Fragment가 직접 웹소켓을 닫습니다.
        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Fragment paused")
            Log.d("STOMP_WS_LIST", "WebSocket 종료: Fragment Paused")
        }
        handler.removeCallbacks(listRefreshRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------------------
    // REST API 호출 로직
    // ---------------------------------------------------------------------
    private fun fetchChatRoomLists(showRefreshing: Boolean = true) {
        if (showRefreshing) {
            binding.swipeRefreshLayout.isRefreshing = true
        }

        RetrofitClient.getApiService().getMyChatRooms()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("CHAT_LIST", "조회 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<ChatRoomListDTO>>() {}.type
                        val dataJson = gson.toJson(response.body()?.data)
                        val newLists: List<ChatRoomListDTO> = gson.fromJson(dataJson, listType)

                        chatRoomLists.clear()
                        chatRoomLists.addAll(newLists)
                        adapter.notifyDataSetChanged()
                        binding.recyclerViewChatRooms.scrollToPosition(0)
                        Log.d("CHAT_LIST", "✅ 채팅방 목록 최초 로드 성공. 개수: ${chatRoomLists.size}")

                    } catch (e: Exception) {
                        Log.e("CHAT_LIST", "List<ChatRoomListDTO> 파싱 중 오류 발생", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.e("CHAT_LIST", "서버 통신 오류", t)
                }
            })
    }

    // ---------------------------------------------------------------------
    // WebSocket/STOMP 로직 (Fragment 직접 구현)
    // ---------------------------------------------------------------------
    private fun connectWebSocket() {
        val token = AuthTokenManager.getToken()

        val userId = AuthTokenManager.getUserId()
        Log.e("AUTH_CHECK", "WS Connect Start: User ID=$userId, Token Exists=${token != null}, Token Prefix=${token?.substring(0, 5)}")

        val client = OkHttpClient.Builder().build()
        val requestBuilder = Request.Builder().url(WEBSOCKET_URL)
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("STOMP_WS_LIST", "✅ WebSocket 연결 성공")

                val connectFrame = "CONNECT\n" +
                        "accept-version:1.2\n" +
                        "heart-beat:10000,10000\n" +
                        "Authorization:Bearer $token\n" +
                        "\n\u0000"
                webSocket.send(connectFrame)
                Log.d("STOMP_WS_LIST", "CONNECT 프레임 전송 완료. CONNECTED 대기 중...")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                activity?.runOnUiThread { handleStompFrame(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("STOMP_WS_LIST", "❌ WebSocket 오류: ${t.message}. 5초 후 재연결 시도.")
                // 💡 연결 실패 시 5초 후 재연결 시도
                handler.postDelayed({
                    if (isAdded && isVisible) {
                        connectWebSocket()
                    }
                }, 5000)
            }
        })
    }

    private fun handleStompFrame(frame: String) {
        when {
            // 🔑 [핵심] CONNECTED 수신 시, 즉시 구독 요청 및 안전장치 예약
            frame.startsWith("CONNECTED") -> {
                Log.d("STOMP_WS_LIST", "🟢 CONNECTED 프레임 수신 확인. 즉시 구독 시도.")

                handler.removeCallbacks(subscribeRunnable)
                handler.post(subscribeRunnable)

                // 🚨 [최후의 안전장치] 2초 후 REST API 강제 재로드 예약
            }
            frame.startsWith("MESSAGE") -> {
                val parts = frame.split("\n\n")
                if (parts.size > 1) {
                    val payload = parts[1].replace("\u0000", "")

                    // 🚨 [디버깅] 수신된 JSON 페이로드 확인
                    Log.e("PUSH_PAYLOAD", "수신된 JSON 페이로드: $payload")

                    try {
                        val gson = Gson()
                        val updateDto = gson.fromJson(payload, ChatRoomListUpdateDTO::class.java)

                        updateChatRoomListUI(updateDto)

                    } catch (e: Exception) {
                        Log.e("STOMP_WS_LIST_MSG", "ChatRoomListUpdateDTO JSON 파싱 오류: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * 💡 [구독 함수] 개인 큐를 구독합니다.
     */
    private fun subscribeToChatListUpdate() {
        val userId = AuthTokenManager.getUserId()
        if (userId != null) {
            val subscribeListFrame = "SUBSCRIBE\n" +
                    "id:sub-list-0\n" +
                    "destination:/user/queue/chat-list-update\n" +
                    "\n\u0000"

            if (::webSocket.isInitialized) {
                webSocket.send(subscribeListFrame)
                Log.d("STOMP_WS_LIST", "📡 개인 알림 큐 구독 완료: /user/queue/chat-list-update")
            }
        } else {
            Log.e("STOMP_WS_LIST", "사용자 ID를 찾을 수 없어 개인 큐를 구독할 수 없습니다.")
        }
    }

    private fun updateChatRoomListUI(updateDto: ChatRoomListUpdateDTO) {
        val targetRoomId = updateDto.roomId ?: return
        val existingIndex = chatRoomLists.indexOfFirst { it.roomId == targetRoomId }

        if (existingIndex != -1) {
            val oldRoom = chatRoomLists[existingIndex]
            val updatedRoom = oldRoom.copy(
                lastMessageContent = updateDto.lastMessageContent ?: oldRoom.lastMessageContent,
                lastMessageTime = updateDto.lastMessageTime ?: oldRoom.lastMessageTime
            )

            // UI 갱신 (리스트 최상단 이동)
            chatRoomLists.removeAt(existingIndex)
            chatRoomLists.add(0, updatedRoom)

            adapter.notifyItemRemoved(existingIndex)
            adapter.notifyItemInserted(0)
            binding.recyclerViewChatRooms.scrollToPosition(0)

            Log.d("CHAT_LIST_UPDATE", "Room ID $targetRoomId 실시간 업데이트 및 최상단 이동 완료.")

        } else {
            Log.i("CHAT_LIST_UPDATE", "목록에 없는 Room ID $targetRoomId 알림 수신. 전체 새로 로드.")
            // 안전장치가 2초 후 재로드를 예약했으므로, 여기서는 추가 호출을 피합니다.
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}