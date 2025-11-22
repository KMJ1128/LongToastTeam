package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityHomeHostBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.MsgEntity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FragmentManager에 리스너 추가: Fragment 스택 변경 시 호출됨
        setupBackStackListener()

        // 1. 앱 시작 시 기본 화면(HomeFragment) 설정
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // 2. Bottom Navigation Listener 설정
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // NewPostFragment가 아닌 기본 Fragment로 이동할 때는 하단바를 보이게 유지
            setBottomNavVisibility(View.VISIBLE)

            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_message -> {
                    replaceFragment(MessageFragment())
                    true
                }
                R.id.nav_my_items -> {
                    replaceFragment(MyItemsFragment())
                    true
                }
                else -> false
            }
        }

        // 3. FAB (새 게시글 작성) 리스너 설정
        binding.fabNewPost.setOnClickListener {
            // NewPostFragment를 띄우기 전에 하단바와 FAB를 숨깁니다.
            setBottomNavVisibility(View.GONE)

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, NewPostFragment())
                .addToBackStack(null) // 뒤로 가기 시 복귀 가능하도록 스택에 추가
                .commit()
        }

        // 4. [신규 추가] 채팅 테스트 버튼 리스너
        binding.fabTestChat.setOnClickListener {
            //createChatRoomAndStartActivity()
            val intent = Intent(this, ReviewActivity::class.java)
            intent.putExtra("TRANSACTION_ID", 1)
            startActivity(intent)
        }
    }

    // Fragment 교체 함수 (Bottom Nav 항목 선택 시 사용)
    private fun replaceFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
        return true
    }

    /**
     * Fragment Back Stack의 변화를 감지하여 하단바 가시성을 관리하는 리스너 설정
     */
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            // 현재 Fragment가 Back Stack에서 제거되었는지 확인
            val fragmentCount = supportFragmentManager.backStackEntryCount

            // Back Stack이 비어있으면 (즉, HomeHostActivity의 기본 Fragment들만 남아있으면)
            if (fragmentCount == 0) {
                // NewPostFragment가 닫히고 기본 화면으로 돌아왔으므로, 하단바를 다시 표시합니다.
                setBottomNavVisibility(View.VISIBLE)
            }
            // NewPostFragment는 BackStack에 추가될 때 setBottomNavVisibility(View.GONE)에 의해 이미 숨겨져 있습니다.
        }
    }

    /**
     * 하단 네비게이션 바와 FAB의 가시성을 설정하는 함수
     */
    private fun setBottomNavVisibility(visibility: Int) {
        binding.bottomNavigation.visibility = visibility
        binding.fabNewPost.visibility = visibility
        binding.fabTestChat.visibility = visibility // [수정] 새 버튼도 같이 제어
    }


    // ----------------------------------------------------------------
    // [신규 추가] SearchResultActivity에서 가져온 채팅방 생성 로직
    // ----------------------------------------------------------------
    /**
     * 1. (테스트) 채팅방 생성 API를 호출하고
     * 2. (성공 시) ChatRoomActivity를 시작하는 함수
     */
    private fun createChatRoomAndStartActivity() {
        Log.d("CHAT_TEST", "채팅방 생성 API 호출 시작...")

        // [수정] AuthTokenManager에서 현재 로그인한 사용자 ID(Borrower) 가져오기
        val currentUserId = AuthTokenManager.getUserId()
        if (currentUserId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다. (테스트 실패)", Toast.LENGTH_SHORT).show()
            Log.e("CHAT_TEST", "현재 사용자 ID (Borrower)를 가져올 수 없습니다.")
            return
        }

        // 테스트용 ID 값들 (Int)
        val testItemId = 1
        val testLenderId = 1 // 이 아이템의 판매자 ID
        val testBorrowerId = currentUserId // 현재 로그인한 사용자
        val testSellerNickname = "테스트 판매자 닉네임 (Lender 1)"

        // [추가] 자기 자신과 채팅방을 만들 수 없음
        if (testLenderId == testBorrowerId) {
            Toast.makeText(this, "자신과의 채팅방은 만들 수 없습니다.", Toast.LENGTH_SHORT).show()
            Log.w("CHAT_TEST", "판매자와 구매자가 동일합니다. (ID: $currentUserId)")
            return
        }

        // DTO 생성
        val request = ChatRoomCreateRequest(
            itemId = testItemId,
            lenderId = testLenderId,
            borrowerId = testBorrowerId
        )

        // API 호출
        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful || response.body() == null) {
                        val errorMsg = response.errorBody()?.string() ?: "알 수 없는 오류"
                        Log.e("CHAT_API", "채팅방 생성 실패 (서버 응답 오류): ${response.code()} / $errorMsg")
                        Toast.makeText(this@HomeHostActivity, "채팅방 생성 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                        return
                    }

                    val rawData = response.body()?.data
                    var roomIdString: String? = null

                    try {
                        val gson = Gson()
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val mapData: Map<String, String>? = gson.fromJson(gson.toJson(rawData), type)
                        roomIdString = mapData?.get("roomId")
                    } catch (e: Exception) {
                        Log.e("CHAT_API", "Room ID 파싱 실패. 원인: ${e.message}", e)
                    }

                    if (roomIdString.isNullOrEmpty()) {
                        Log.e("CHAT_API", "Room ID 획득 실패. 최종 파싱 결과: $roomIdString")
                        Toast.makeText(this@HomeHostActivity, "Room ID 획득 실패", Toast.LENGTH_LONG).show()
                        return
                    }

                    Log.d("CHAT_API", "채팅방 생성 성공. Room ID: $roomIdString")
                    Toast.makeText(this@HomeHostActivity, "채팅방이 생성되었습니다. ID: $roomIdString", Toast.LENGTH_SHORT).show()

                    // 4. ChatRoomActivity 시작 (roomId 전달)
                    val intent = Intent(this@HomeHostActivity, ChatRoomActivity::class.java).apply {
                        putExtra("PRODUCT_ID", testItemId.toString())
                        putExtra("SELLER_NICKNAME", testSellerNickname)
                        putExtra("ROOM_ID", roomIdString) // 유효한 roomId 전달
                    }
                    startActivity(intent)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("CHAT_API", "서버 통신 오류", t)
                    Toast.makeText(this@HomeHostActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
    }
}