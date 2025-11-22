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
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())

    private val subscribeRunnable = Runnable {
        subscribeToChatListUpdate()
    }

    private val listRefreshRunnable = object : Runnable {
        override fun run() {
            fetchChatRoomLists(showRefreshing = false)
            handler.postDelayed(this, 10_000)
        }
    }

    private val WEBSOCKET_URL = ServerConfig.WEBSOCKET_URL
    private lateinit var webSocket: WebSocket

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
            fetchChatRoomLists()
        }
    }

    override fun onResume() {
        super.onResume()
        fetchChatRoomLists()
        connectWebSocket()
        handler.postDelayed(listRefreshRunnable, 10_000)
    }

    override fun onPause() {
        super.onPause()

        if (::webSocket.isInitialized) {
            webSocket.close(1000, "Fragment paused")
            Log.d("STOMP_WS_LIST", "WebSocket 종료")
        }

        handler.removeCallbacks(listRefreshRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    private fun fetchChatRoomLists(showRefreshing: Boolean = true) {
        if (showRefreshing) binding.swipeRefreshLayout.isRefreshing = true

        RetrofitClient.getApiService().getMyChatRooms()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    binding.swipeRefreshLayout.isRefreshing = false

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

                        ChatNotificationHelper.saveSnapshot(
                            requireContext().applicationContext,
                            chatRoomLists
                        )

                    } catch (e: Exception) {
                        Log.e("CHAT_LIST", "파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.e("CHAT_LIST", "서버 통신 실패", t)
                }
            })
    }

    private fun connectWebSocket() {
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
                    activity?.runOnUiThread {
                        handleStompFrame(text)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e("STOMP_WS_LIST", "WebSocket 오류: ${t.message}")

                    handler.postDelayed({
                        if (isAdded && isVisible) connectWebSocket()
                    }, 5000)
                }
            })
    }

    private fun handleStompFrame(frame: String) {
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
        if (!::webSocket.isInitialized) return

        val frame = "SUBSCRIBE\n" +
                "id:sub-list-0\n" +
                "destination:/user/queue/chat-list-update\n\n\u0000"

        webSocket.send(frame)
        Log.d("STOMP_WS_LIST", "큐 구독 완료")
    }

    private fun updateChatRoomListUI(updateDto: ChatRoomListUpdateDTO) {
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

            adapter.notifyItemRemoved(idx)
            adapter.notifyItemInserted(0)

            binding.recyclerViewChatRooms.scrollToPosition(0)

            ChatNotificationHelper.saveSnapshot(
                requireContext().applicationContext,
                chatRoomLists
            )

        } else {
            Log.i("CHAT_LIST_UPDATE", "목록에 없는 Room → 전체 새로 로드 필요")
            fetchChatRoomLists()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}
