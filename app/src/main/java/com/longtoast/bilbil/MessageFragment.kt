package com.longtoast.bilbil

import android.content.Intent
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
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MessageFragment : Fragment() {

    private var _binding: FragmentMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerViewChatRooms.layoutManager = LinearLayoutManager(context)
        fetchChatRoomLists()
    }

    /**
     * 서버에서 현재 사용자의 채팅방 목록을 불러옵니다.
     */
    private fun fetchChatRoomLists() {
        Log.d("CHAT_LIST", "채팅방 목록 조회 API 호출 시작...")

        RetrofitClient.getApiService().getMyChatRooms()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("CHAT_LIST", "조회 실패: ${response.code()}")
                        Toast.makeText(context, "채팅방 목록을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val rawData = response.body()?.data
                    var roomLists: List<ChatRoomListDTO>? = null

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<ChatRoomListDTO>>() {}.type
                        val dataJson = gson.toJson(rawData)
                        roomLists = gson.fromJson(dataJson, listType)
                    } catch (e: Exception) {
                        Log.e("CHAT_LIST", "List<ChatRoomListDTO> 파싱 중 오류 발생", e)
                    }

                    if (roomLists != null && roomLists.isNotEmpty()) {
                        Log.d("CHAT_LIST", "✅ 채팅방 목록 조회 성공. 개수: ${roomLists.size}")

                        val adapter = ChatRoomListAdapter(roomLists) { room ->
                            val intent = Intent(requireContext(), ChatRoomActivity::class.java)
                            intent.putExtra("ROOM_ID", room.roomId.toString())
                            intent.putExtra("SELLER_NICKNAME", room.partnerNickname)
                            startActivity(intent)
                        }

                        binding.recyclerViewChatRooms.adapter = adapter
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
}
