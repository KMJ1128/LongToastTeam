package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityHomeHostBinding
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
        setupDrawerMenu() // 여기서 함수 호출

        // 프로필 정보 로드
        loadNavigationHeader()
    }

    // Navigation Header 프로필 정보 로드
    private fun loadNavigationHeader() {
        RetrofitClient.getApiService().getMyInfo()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Log.e("NAV_HEADER", "프로필 로드 실패: ${response.code()}")
                        return
                    }

                    val raw = response.body()?.data ?: return

                    try {
                        val gson = Gson()
                        val type = object : TypeToken<MemberDTO>() {}.type
                        val member: MemberDTO = gson.fromJson(gson.toJson(raw), type)

                        // 헤더 뷰 가져오기
                        val headerView = binding.navView.getHeaderView(0)
                        val profileImageView =
                            headerView.findViewById<ImageView>(R.id.nav_header_profile_image)
                        val nicknameTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_nickname)
                        val creditScoreTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_credit_score)
                        val addressTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_address)

                        // 닉네임 설정
                        nicknameTextView.text = member.nickname ?: "닉네임 미지정"

                        // 신용점수 설정
                        creditScoreTextView.text = "신용점수: ${member.creditScore ?: 720}점"

                        // 주소 설정
                        addressTextView.text = member.address ?: "위치 미지정"

                        // 프로필 이미지 로드
                        val imageUrl = member.profileImageUrl
                        if (!imageUrl.isNullOrEmpty()) {
                            val fullUrl = ImageUrlUtils.resolve(imageUrl)
                            Glide.with(this@HomeHostActivity)
                                .load(fullUrl)
                                .circleCrop()
                                .placeholder(R.drawable.no_profile)
                                .error(R.drawable.no_profile)
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageResource(R.drawable.no_profile)
                        }

                        // SharedPreferences에 저장 (재로그인 시 유지)
                        AuthTokenManager.saveNickname(member.nickname ?: "")
                        AuthTokenManager.saveAddress(member.address ?: "")

                    } catch (e: Exception) {
                        Log.e("NAV_HEADER", "MemberDTO 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("NAV_HEADER", "네트워크 오류", t)
                    // 네트워크 실패 시 SharedPreferences에서 로드
                    loadFromSharedPreferences()
                }
            })
    }

    // SharedPreferences에서 프로필 정보 로드 (오프라인 대비)
    private fun loadFromSharedPreferences() {
        val headerView = binding.navView.getHeaderView(0)
        val nicknameTextView = headerView.findViewById<TextView>(R.id.nav_header_nickname)
        val addressTextView = headerView.findViewById<TextView>(R.id.nav_header_address)

        val nickname = AuthTokenManager.getNickname()
        val address = AuthTokenManager.getAddress()

        nicknameTextView.text = nickname ?: "닉네임 미지정"
        addressTextView.text = address ?: "위치 미지정"
    }

    // Drawer 열기 메서드
    fun openDrawer() {
        binding.drawerLayout.openDrawer(binding.navView)
    }

    // ✅ DrawerMenu 아이템 클릭 처리 (여기에 모든 메뉴 로직 통합)
    private fun setupDrawerMenu() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_edit_profile -> {
                    // 프로필 수정 화면으로 이동
                    val intent = Intent(this, EditProfileActivity::class.java)
                    editProfileLauncher.launch(intent)
                }

                // ✅ 내가 쓴 리뷰 (역할 탭: 대여자/사용자)
                R.id.nav_my_reviews -> {
                    val intent = Intent(this, ReviewListActivity::class.java)
                    intent.putExtra("REVIEW_TYPE", "MY_WRITTEN")
                    startActivity(intent)
                }

                // ✅ 내가 받은 리뷰 (역할 탭: 대여자/사용자)
                R.id.nav_received_reviews -> {
                    val intent = Intent(this, ReviewListActivity::class.java)
                    intent.putExtra("REVIEW_TYPE", "MY_RECEIVED")
                    startActivity(intent)
                }

                // ✅ 내 물건 빌려간 사람 리뷰 쓰기
                R.id.nav_lender_review_targets -> {
                    val intent = Intent(this, LenderReviewTargetsActivity::class.java)
                    startActivity(intent)
                }

                R.id.nav_sign_out -> {
                    AlertDialog.Builder(this)
                        .setTitle("로그아웃")
                        .setMessage("로그아웃 하시겠습니까?")
                        .setPositiveButton("확인") { _, _ ->
                            // 토큰만 삭제 (닉네임, 주소는 유지)
                            AuthTokenManager.clearToken()
                            AuthTokenManager.clearUserId()

                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }

                R.id.nav_nagari -> {
                    AlertDialog.Builder(this)
                        .setTitle("회원탈퇴")
                        .setMessage("정말 탈퇴하시겠습니까? 모든 데이터가 삭제됩니다.")
                        .setPositiveButton("탈퇴") { _, _ ->
                            // 회원탈퇴 시에만 모든 데이터 삭제
                            AuthTokenManager.clearAll()
                            Toast.makeText(this, "회원탈퇴가 완료되었습니다", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
            binding.drawerLayout.closeDrawer(binding.navView)
            true
        }
    }

    // 프로필 수정 결과 처리
    private val editProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // 프로필 수정 후 헤더 정보 새로고침
                loadNavigationHeader()
                Toast.makeText(this, "프로필이 업데이트되었습니다", Toast.LENGTH_SHORT).show()
            }
        }

    // Fragment 초기화 및 add + hide/show
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

    // BottomNavigation
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

    // FAB
    private fun setupFABs() {
        binding.fabNewPost.setOnClickListener {
            setBottomNavVisibility(View.GONE)
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, NewPostFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    fun setBottomNavVisibility(visibility: Int) {
        binding.bottomNavigation.visibility = visibility
        binding.fabNewPost.visibility = visibility
    }

    // BackStack listener
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setBottomNavVisibility(View.VISIBLE)
            }
        }
    }
}
