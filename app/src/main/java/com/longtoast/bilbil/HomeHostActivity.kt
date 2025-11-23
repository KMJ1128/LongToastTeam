package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.longtoast.bilbil.databinding.ActivityHomeHostBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.TextView

class HomeHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeHostBinding

    private lateinit var homeFragment: HomeFragment
    private lateinit var messageFragment: MessageFragment
    private lateinit var myItemsFragment: MyItemsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNav()
        setupFABs()
        setupBackStackListener()
        setupNavigationHeader()  // 🆕 이 줄 추가
    }

    // 새로운 메서드 추가
    private fun setupNavigationHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val nicknameTextView = headerView.findViewById<TextView>(R.id.nav_header_nickname)
        val addressTextView = headerView.findViewById<TextView>(R.id.nav_header_address)

        // 닉네임 표시
        val nickname = AuthTokenManager.getNickname()
        nicknameTextView.text = nickname ?: "이름 미설정"

        // 🆕 주소 표시
        val address = AuthTokenManager.getAddress()
        addressTextView.text = address ?: "위치 미설정"
    }

    // ----------------------------
    // Fragment 초기화 및 add + hide/show
    // ----------------------------
    private fun setupFragments() {
        val fm = supportFragmentManager
        homeFragment = HomeFragment()
        messageFragment = MessageFragment()
        myItemsFragment = MyItemsFragment()

        val transaction = fm.beginTransaction()
        transaction.add(R.id.main_fragment_container, homeFragment, "HOME")
            .add(R.id.main_fragment_container, messageFragment, "MESSAGE").hide(messageFragment)
            .add(R.id.main_fragment_container, myItemsFragment, "MY_ITEMS").hide(myItemsFragment)
            .commit()
    }

    private fun showFragment(fragment: Fragment) {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()
        fm.fragments.forEach { transaction.hide(it) }
        transaction.show(fragment).commit()
    }

    // ----------------------------
    // BottomNavigation
    // ----------------------------
    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            setBottomNavVisibility(View.VISIBLE)

            when (item.itemId) {
                R.id.nav_home -> showFragment(homeFragment)
                R.id.nav_message -> showFragment(messageFragment)
                R.id.nav_my_items -> showFragment(myItemsFragment)
            }
            true
        }
    }

    // ----------------------------
    // FAB
    // ----------------------------
    private fun setupFABs() {
        binding.fabNewPost.setOnClickListener {
            setBottomNavVisibility(View.GONE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, NewPostFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.fabTestChat.setOnClickListener { createChatRoomAndStartActivity() }
    }

    private fun setBottomNavVisibility(visibility: Int) {
        binding.bottomNavigation.visibility = visibility
        binding.fabNewPost.visibility = visibility
        binding.fabTestChat.visibility = visibility
    }

    // ----------------------------
    // BackStack listener
    // ----------------------------
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setBottomNavVisibility(View.VISIBLE)
            }
        }
    }

    // ----------------------------
    // Chat Room 생성 테스트
    // ----------------------------
    private fun createChatRoomAndStartActivity() {
        val currentUserId = AuthTokenManager.getUserId()
        if (currentUserId == null) {
            Toast.makeText(this, "로그인 정보 없음", Toast.LENGTH_SHORT).show()
            return
        }

        val testItemId = 1
        val testLenderId = 1 // 이 아이템의 판매자 ID
        val testBorrowerId = currentUserId // 현재 로그인한 사용자
        val testSellerNickname = "테스트 판매자 닉네임 (Lender 1)"

        if (testLenderId == testBorrowerId) return

        val request = ChatRoomCreateRequest(testItemId, testLenderId, testBorrowerId)
        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val rawData = response.body()?.data ?: return
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val mapData: Map<String, String>? = gson.fromJson(gson.toJson(rawData), type)
                    val roomId = mapData?.get("roomId") ?: return

                    val intent = Intent(this@HomeHostActivity, ChatRoomActivity::class.java)
                    intent.putExtra("PRODUCT_ID", testItemId.toString())
                    intent.putExtra("SELLER_NICKNAME", testSellerNickname)
                    intent.putExtra("ROOM_ID", roomId)
                    startActivity(intent)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {}
            })
    }
}
