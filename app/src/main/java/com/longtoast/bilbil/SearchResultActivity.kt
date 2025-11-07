package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.ChatMsgEntity // 🚨 임포트 변경
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ‼️ 임시 레이아웃(activity_setting_profile)을 사용합니다.
        setContentView(R.layout.activity_setting_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val testChatButton: Button = findViewById(R.id.button_complete)
        testChatButton.text = "채팅방 생성 테스트 버튼"

        testChatButton.setOnClickListener {
            createChatRoomAndStartActivity()
        }
    }

    /**
     * 1. (테스트) 채팅방 생성 API를 호출하고
     * 2. (성공 시) ChatRoomActivity를 시작하는 함수
     */
    private fun createChatRoomAndStartActivity() {
        Log.d("CHAT_TEST", "채팅방 생성 API 호출 시작...")

        // 테스트용 ID 값들 (Int)
        val testItemId = 1
        val testLenderId = 1
        val testBorrowerId = 2
        val testSellerNickname = "테스트 판매자"

        // DTO 생성
        val request = ChatRoomCreateRequest(
            itemId = testItemId,
            lenderId = testLenderId,
            borrowerId = testBorrowerId
        )

        // API 호출
        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<ChatMsgEntity> { // 💡 ChatMsgEntity 사용

                override fun onResponse(call: Call<ChatMsgEntity>, response: Response<ChatMsgEntity>) {
                    // 1. 서버 응답 실패 처리
                    if (!response.isSuccessful || response.body() == null) {
                        val errorMsg = response.errorBody()?.string() ?: "알 수 없는 오류"
                        Log.e("CHAT_API", "채팅방 생성 실패 (서버 응답 오류): ${response.code()} / $errorMsg")
                        Toast.makeText(this@SearchResultActivity, "채팅방 생성 실패: ${response.code()} / $errorMsg", Toast.LENGTH_LONG).show()
                        return
                    }

                    // -------------------------------------------------
                    // 🚨 [핵심 수정] - DTO 체인을 통해 roomId에 직접 접근 (자동 파싱 활용)
                    // -------------------------------------------------
                    val chatMsgEntity = response.body()
                    val roomIdString = chatMsgEntity?.data?.roomId // 💡 Null safety를 적용하여 직접 String 추출

                    Log.d("CHAT_API_RAW_DATA", "서버 data 필드 내용: ${chatMsgEntity?.data.toString()}")


                    // 2. roomId 검증 및 다음 단계로 진행
                    if (roomIdString.isNullOrEmpty()) {
                        Log.e("CHAT_API", "Room ID 획득 실패. 최종 파싱 결과: $roomIdString")
                        Toast.makeText(this@SearchResultActivity, "Room ID 획득 실패", Toast.LENGTH_LONG).show()
                        return
                    }

                    // 3. roomId 파싱 성공
                    Log.d("CHAT_API", "채팅방 생성 성공. Room ID: $roomIdString")
                    Toast.makeText(this@SearchResultActivity, "채팅방이 생성되었습니다. ID: $roomIdString", Toast.LENGTH_SHORT).show()

                    // 4. ChatRoomActivity 시작 (roomId 전달)
                    val intent = Intent(this@SearchResultActivity, ChatRoomActivity::class.java).apply {
                        putExtra("PRODUCT_ID", testItemId.toString())
                        putExtra("SELLER_NICKNAME", testSellerNickname)
                        putExtra("ROOM_ID", roomIdString) // 유효한 roomId 전달
                    }
                    startActivity(intent)
                }

                override fun onFailure(call: Call<ChatMsgEntity>, t: Throwable) {
                    Log.e("CHAT_API", "서버 통신 오류", t)
                    Toast.makeText(this@SearchResultActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
