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
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.longtoast.bilbil.dto.ChatRoomListUpdateDTO
import com.longtoast.bilbil.ChatNotificationHelper
import com.longtoast.bilbil.ChatRoomListParser
import com.google.gson.Gson
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding: FragmentMessageBinding
        get() = _binding
            ?: throw IllegalStateException("Binding is only valid between onCreateView and onDestroyView")

    private val handler = Handler(Looper.getMainLooper())

    private val subscribeRunnable = Runnable {
        subscribeToChatListUpdate()
    }

    /**
     * 일정 주기로 채팅방 목록을 새로고침하는 Runnable
     * - Fragment가 화면에 없거나 View가 없는 상태이면 아무것도 하지 않음
     */
    private val listRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || _binding == null) {
                // Fragment가 더 이상 유효하지 않으면 주기 갱신 중단
                return
            }
            fetchChatRoomLists(showRefreshing = false)
            handler.postDelayed(this, 10_000)
        }
    }

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private var webSocket: WebSocket? = null

    private val chatRoomLists = mutableListOf<ChatRoomListDTO>()
    private lateinit var adapter: ChatRoomListAdapter

    // ─────────────────────────────────────────────────────────────────────────────
    // Fragment Lifecycle
    // ─────────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatRoomListAdapter(chatRoomLists) { room ->
            val intent = Intent(requireContext(), ChatRoomActivity::class.java).apply {
                putExtra("ROOM_ID", room.roomId.toString())
                putExtra("SELLER_NICKNAME", room.partnerNickname)
            }
            startActivity(intent)
        }

        binding.recyclerViewChatRooms.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewChatRooms.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchChatRoomLists()
        }
    }

    override fun onResume() {
        super.onResume()

        // View가 살아 있을 때만 동작
        if (_binding != null) {
            fetchChatRoomLists()
            connectWebSocket()
            handler.postDelayed(listRefreshRunnable, 10_000)
        }
    }

    override fun onPause() {
        super.onPause()
        disconnectWebSocket()
        handler.removeCallbacks(listRefreshRunnable)
        handler.removeCallbacks(subscribeRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 네트워크 - 채팅방 목록 조회
    // ─────────────────────────────────────────────────────────────────────────────

    private fun fetchChatRoomLists(showRefreshing: Boolean = true) {
        val binding = _binding ?: return  // View가 이미 파괴됐으면 아무 것도 하지 않음

        if (showRefreshing) binding.swipeRefreshLayout.isRefreshing = true

        RetrofitClient.getApiService().getMyChatRooms()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val binding = _binding ?: return  // 콜백 들어왔을 때도 다시 체크
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (!isAdded) return  // Fragment가 Activity에 붙어있지 않으면 종료

                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("CHAT_LIST", "조회 실패: ${response.code()}")
                        return
                    }

                    try {
                        val newLists =
                            ChatRoomListParser.parseFromMsgEntity(response.body())

                        chatRoomLists.clear()
                        chatRoomLists.addAll(newLists)
                        adapter.notifyDataSetChanged()

                        binding.recyclerViewChatRooms.scrollToPosition(0)

                        val appContext = context?.applicationContext ?: return
                        ChatNotificationHelper.saveSnapshot(
                            appContext,
                            chatRoomLists
                        )

                    } catch (e: Exception) {
                        Log.e("CHAT_LIST", "파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    val binding = _binding ?: return
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.e("CHAT_LIST", "서버 통신 실패", t)
                }
            })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // WebSocket / STOMP 연결 관리
    // ─────────────────────────────────────────────────────────────────────────────

    private fun connectWebSocket() {
        if (!isAdded || _binding == null) return

        val token = AuthTokenManager.getToken()
        val client = OkHttpClient.Builder().build()

        val requestBuilder = Request.Builder().url(WEBSOCKET_URL)

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        webSocket = client.newWebSocket(requestBuilder.build(),
            object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    val connectFrame = "CONNECT\n" +
                            "accept-version:1.2\n" +
                            "heart-beat:10000,10000\n" +
                            "Authorization:Bearer $token\n\n\u0000"

                    webSocket.send(connectFrame)
                    Log.d("STOMP_WS_LIST", "CONNECT 전송 완료")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // UI 스레드에서 동작하되, Fragment/뷰 상태를 다시 확인
                    activity?.runOnUiThread {
                        if (!isAdded || _binding == null) return@runOnUiThread
                        handleStompFrame(text)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e("STOMP_WS_LIST", "WebSocket 오류: ${t.message}")

                    // 재연결 시도도 Fragment가 살아있을 때만
                    handler.postDelayed({
                        if (isAdded && _binding != null) {
                            connectWebSocket()
                        }
                    }, 5000)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    Log.d("STOMP_WS_LIST", "WebSocket 종료: $code / $reason")
                }
            })
    }

    private fun disconnectWebSocket() {
        webSocket?.let {
            try {
                it.close(1000, "Fragment paused")
                Log.d("STOMP_WS_LIST", "WebSocket 종료")
            } catch (e: Exception) {
                Log.e("STOMP_WS_LIST", "WebSocket 종료 중 예외", e)
            }
        }
        webSocket = null
    }

    private fun handleStompFrame(frame: String) {
        if (!isAdded || _binding == null) return

        when {
            frame.startsWith("CONNECTED") -> {
                handler.removeCallbacks(subscribeRunnable)
                handler.post(subscribeRunnable)
            }

            frame.startsWith("MESSAGE") -> {
                val payload =
                    frame.split("\n\n").getOrNull(1)?.replace("\u0000", "") ?: return

                try {
                    val updateDto =
                        Gson().fromJson(payload, ChatRoomListUpdateDTO::class.java)
                    updateChatRoomListUI(updateDto)
                } catch (e: Exception) {
                    Log.e("STOMP_WS_LIST_MSG", "JSON 파싱 오류", e)
                }
            }
        }
    }

    private fun subscribeToChatListUpdate() {
        val socket = webSocket ?: return

        val frame = "SUBSCRIBE\n" +
                "id:sub-list-0\n" +
                "destination:/user/queue/chat-list-update\n\n\u0000"

        socket.send(frame)
        Log.d("STOMP_WS_LIST", "큐 구독 완료")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 채팅방 목록 UI 업데이트
    // ─────────────────────────────────────────────────────────────────────────────

    private fun updateChatRoomListUI(updateDto: ChatRoomListUpdateDTO) {
        val binding = _binding ?: return
        if (!isAdded) return

        val roomId = updateDto.roomId ?: return

        val idx = chatRoomLists.indexOfFirst { it.roomId == roomId }

        if (idx != -1) {
            val old = chatRoomLists[idx]

            val updated = old.copy(
                lastMessageContent = updateDto.lastMessageContent ?: old.lastMessageContent,
                lastMessageTime = updateDto.lastMessageTime ?: old.lastMessageTime
            )

            chatRoomLists.removeAt(idx)
            chatRoomLists.add(0, updated)

            // 위치 변경 애니메이션 적용
            adapter.notifyItemRemoved(idx)
            adapter.notifyItemInserted(0)

            binding.recyclerViewChatRooms.scrollToPosition(0)

            val appContext = context?.applicationContext ?: return
            ChatNotificationHelper.saveSnapshot(
                appContext,
                chatRoomLists
            )

        } else {
            Log.i("CHAT_LIST_UPDATE", "목록에 없는 Room → 전체 새로 로드 필요")
            fetchChatRoomLists()
        }
    }
}
