package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.FragmentMessageBinding
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatRoomListDTO // 🚨 DTO 임포트
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// Gson 파싱을 위한 Type 변환 도구 (List 파싱에 필요)
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    // 🚨 TODO: 채팅방 목록 어댑터 정의 필요
    // private lateinit var roomListAdapter: ChatRoomListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. RecyclerView 설정 (어댑터는 데이터 로드 후 설정)
        binding.recyclerViewChatRooms.layoutManager = LinearLayoutManager(context)

        // 2. 채팅방 목록 조회 API 호출
        fetchChatRoomLists()
    }

    /**
     * 서버에서 현재 사용자의 채팅방 목록을 불러옵니다.
     */
    private fun fetchChatRoomLists() {
        Log.d("CHAT_LIST", "채팅방 목록 조회 API 호출 시작...")

        // RetrofitClient에는 AuthInterceptor가 있으므로 토큰은 자동으로 추가됩니다.
        RetrofitClient.getApiService().getMyChatRooms()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("CHAT_LIST", "조회 실패: ${response.code()}")
                        Toast.makeText(context, "채팅방 목록을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // -------------------------------------------------
                    // 🚨 [핵심 파싱 로직] - List<DTO> 안전하게 파싱
                    // -------------------------------------------------
                    val rawData = response.body()?.data
                    var roomLists: List<ChatRoomListDTO>? = null

                    try {
                        // 1. Gson 객체 생성
                        val gson = Gson()

                        // 2. List<ChatRoomListDTO>의 TypeToken 생성
                        val listType = object : TypeToken<List<ChatRoomListDTO>>() {}.type

                        // 3. rawData (Map)를 JSON 문자열로 변환 후, 다시 List<DTO>로 역직렬화
                        val dataJson = gson.toJson(rawData)
                        roomLists = gson.fromJson(dataJson, listType)

                    } catch (e: Exception) {
                        Log.e("CHAT_LIST", "List<ChatRoomListDTO> 파싱 중 오류 발생", e)
                    }

                    // -------------------------------------------------

                    if (roomLists != null && roomLists.isNotEmpty()) {
                        Log.d("CHAT_LIST", "✅ 채팅방 목록 조회 성공. 개수: ${roomLists.size}")
                        // 3. (TODO) RecyclerView에 데이터 바인딩
                        // roomListAdapter.submitList(roomLists)
                        Toast.makeText(context, "채팅방 ${roomLists.size}개 로드 성공", Toast.LENGTH_SHORT)
                            .show()

                        // 🚨 임시 로직: 어댑터가 없으므로 로그만 출력
                        roomLists.forEach {
                            Log.d(
                                "CHAT_ITEM",
                                "Room ID: ${it.roomId}, Partner: ${it.partnerNickname}, LastMsg: ${it.lastMessageContent}"
                            )
                        }

                    } else {
                        Log.i("CHAT_LIST", "조회 결과 없음 또는 파싱된 리스트가 비어있음.")
                        Toast.makeText(context, "참여 중인 채팅방이 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_LIST", "서버 통신 오류", t)
                    Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

// 🚨 TODO: ChatRoomListAdapter (RecyclerView Adapter) 정의가 필요합니다.
}