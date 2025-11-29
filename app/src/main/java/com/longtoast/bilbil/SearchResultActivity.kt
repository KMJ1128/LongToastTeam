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
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivitySearchResultBinding
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductListDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchResultBinding
    private lateinit var adapter: ProductAdapter

    // 현재 검색 상태
    private var currentQuery: String? = null
    private var isCategory: Boolean = false
    private var currentSort: String? = "latest" // 기본: 최신순

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DEBUG_FLOW", "🔥 SearchResultActivity.onCreate() 실행됨")

        binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupDrawerMenu()
        setupRecycler()
        setupSortButton()

        // 전달된 검색 값 확인
        var query = intent.getStringExtra("SEARCH_QUERY")
        isCategory = intent.getBooleanExtra("SEARCH_IS_CATEGORY", false)

        Log.d("DEBUG_FLOW", "전달 받은 원본 검색 정보 → query=$query, isCategory=$isCategory")

        // 🔥 "#:{category}" 형태면 카테고리 검색 모드로 전환 (이전 로직 유지)
        if (!query.isNullOrBlank() && query.startsWith("#:")) {
            isCategory = true
            query = query.removePrefix("#:").trim()
            Log.d("DEBUG_FLOW", "파싱 후 → query=$query | isCategory=$isCategory (카테고리 검색 모드)")
        }

        if (query == null) {
            Log.e("DEBUG_FLOW", "❌ query=null → SearchResultActivity 오류 가능")
        }

        currentQuery = query

        // 헤더 안 검색창 세팅 (초기 검색어 표시)
        setupSearchBar(currentQuery ?: "")

        // 상단 "{검색어} 검색 결과" 텍스트
        binding.queryText.text = if (isCategory) {
            "\"$query\" 카테고리"
        } else {
            "\"$query\" 검색 결과"
        }

        // 첫 로딩은 기본 정렬(최신순)으로
        loadSearchResults(currentQuery, isCategory, currentSort)

        // 헤더/드로어용 프로필 & 위치
        loadMyLocationForHeader()
        loadNavigationHeader()
        updateCartBadge()
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
    }

    // -------------------------------------------------------------
    // 헤더(홈 화면 스타일) 세팅
    // -------------------------------------------------------------
    private fun setupHeader() {
        // 🔹 햄버거 버튼: 메인 화면과 동일하게 드로어 열기
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        // 장바구니 이동
        binding.btnGoCart.setOnClickListener {
            val intent = Intent(this, CartActivity::class.java)
            startActivity(intent)
        }

        // 하단 "이전으로 돌아가기" 버튼
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    // 🔹 헤더 안 검색창 세팅
    private fun setupSearchBar(initialQuery: String) {
        binding.searchBar.apply {
            setIconifiedByDefault(false)
            isIconified = false
            setQuery(initialQuery, false)
            clearFocus()
            queryHint = "근처 물건을 검색해 보세요"

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val keyword = query?.trim().orEmpty()
                    if (keyword.isNotEmpty()) {
                        currentQuery = keyword
                        isCategory = false          // 검색창에서 검색하면 일반 제목 검색
                        currentSort = "latest"      // 정렬은 다시 최신순으로
                        binding.btnSort.text = "최신순"
                        binding.queryText.text = "\"$keyword\" 검색 결과"

                        loadSearchResults(currentQuery, isCategory, currentSort)
                        clearFocus()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            })
        }
    }

    // 헤더용 내 위치 + 상단 프로필 (HomeFragment 느낌)
    private fun loadMyLocationForHeader() {
        RetrofitClient.getApiService().getMyInfo()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) return
                    val raw = response.body()?.data ?: return
                    try {
                        val gson = Gson()
                        val type = object : TypeToken<MemberDTO>() {}.type
                        val member: MemberDTO = gson.fromJson(gson.toJson(raw), type)

                        binding.locationText.text = member.address ?: "내 위치"

                        val fullUrl = ImageUrlUtils.resolve(member.profileImageUrl)
                        if (!fullUrl.isNullOrEmpty()) {
                            Glide.with(this@SearchResultActivity)
                                .load(fullUrl)
                                .circleCrop()
                                .into(binding.profileImage)
                        }
                    } catch (e: Exception) {
                        Log.e("SEARCH_HEADER", "MemberDTO 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("SEARCH_HEADER", "내 위치/프로필 불러오기 실패", t)
                }
            })
    }

    // 장바구니 뱃지 표시
    private fun updateCartBadge() {
        val count = CartManager.getItems().size
        if (count > 0) {
            binding.cartBadge.text = if (count > 99) "99+" else count.toString()
            binding.cartBadge.isVisible = true
        } else {
            binding.cartBadge.isVisible = false
        }
    }

    // -------------------------------------------------------------
    // Drawer + NavigationView (메인과 동일 동작)
    // -------------------------------------------------------------
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

                        val headerView = binding.navView.getHeaderView(0)
                        val profileImageView =
                            headerView.findViewById<ImageView>(R.id.nav_header_profile_image)
                        val nicknameTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_nickname)
                        val creditScoreTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_credit_score)
                        val addressTextView =
                            headerView.findViewById<TextView>(R.id.nav_header_address)

                        nicknameTextView.text = member.nickname ?: "닉네임 미지정"
                        creditScoreTextView.text = "신용점수: ${member.creditScore ?: 720}점"
                        addressTextView.text = member.address ?: "위치 미지정"

                        val imageUrl = member.profileImageUrl
                        if (!imageUrl.isNullOrEmpty()) {
                            val fullUrl = ImageUrlUtils.resolve(imageUrl)
                            Glide.with(this@SearchResultActivity)
                                .load(fullUrl)
                                .circleCrop()
                                .placeholder(R.drawable.no_profile)
                                .error(R.drawable.no_profile)
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageResource(R.drawable.no_profile)
                        }

                        // SharedPreferences 저장 (로그인 정보 유지)
                        AuthTokenManager.saveNickname(member.nickname ?: "")
                        AuthTokenManager.saveAddress(member.address ?: "")

                    } catch (e: Exception) {
                        Log.e("NAV_HEADER", "MemberDTO 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("NAV_HEADER", "네트워크 오류", t)
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

    private fun setupDrawerMenu() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_edit_profile -> {
                    val intent = Intent(this, EditProfileActivity::class.java)
                    editProfileLauncher.launch(intent)
                }

                R.id.nav_my_reviews -> {
                    Toast.makeText(this, "내가 쓴 리뷰", Toast.LENGTH_SHORT).show()
                    // TODO: 내가 쓴 리뷰 화면으로 이동
                }

                R.id.nav_received_reviews -> {
                    Toast.makeText(this, "내가 받은 리뷰", Toast.LENGTH_SHORT).show()
                    // TODO: 내가 받은 리뷰 화면으로 이동
                }

                R.id.nav_sign_out -> {
                    AlertDialog.Builder(this)
                        .setTitle("로그아웃")
                        .setMessage("로그아웃 하시겠습니까?")
                        .setPositiveButton("확인") { _, _ ->
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
                            AuthTokenManager.clearAll()
                            Toast.makeText(this, "회원탈퇴가 완료되었습니다", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finishAffinity()
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            true
        }
    }

    // 프로필 수정 후 드로어 헤더 새로고침
    private val editProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadNavigationHeader()
                loadMyLocationForHeader()
                Toast.makeText(this, "프로필이 업데이트되었습니다", Toast.LENGTH_SHORT).show()
            }
        }

    // -------------------------------------------------------------
    // RecyclerView & Adapter
    // -------------------------------------------------------------
    private fun setupRecycler() {
        adapter = ProductAdapter(emptyList()) { itemId ->
            Log.d("DEBUG_FLOW", "아이템 클릭됨 → itemId=$itemId")
            val intent = Intent(this, ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", itemId)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    // -------------------------------------------------------------
    // 정렬 버튼 세팅
    // -------------------------------------------------------------
    private fun setupSortButton() {
        binding.btnSort.text = "최신순"

        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
    }

    private fun showSortDialog() {
        val items = arrayOf("최신순", "가격 낮은 순", "가격 높은 순")

        AlertDialog.Builder(this)
            .setTitle("정렬 기준")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { // 최신순
                        currentSort = "latest"
                        binding.btnSort.text = "최신순"
                    }

                    1 -> { // 가격 낮은 순
                        currentSort = "price_asc"
                        binding.btnSort.text = "가격 낮은 순"
                    }

                    2 -> { // 가격 높은 순
                        currentSort = "price_desc"
                        binding.btnSort.text = "가격 높은 순"
                    }
                }
                loadSearchResults(currentQuery, isCategory, currentSort)
            }
            .show()
    }

    // -------------------------------------------------------------
    // 서버 통신: 검색 결과 + 정렬
    // -------------------------------------------------------------
    private fun loadSearchResults(query: String?, isCategory: Boolean, sort: String?) {

        Log.d(
            "DEBUG_FLOW",
            "loadSearchResults() 호출됨 / query=$query, isCategory=$isCategory, sort=$sort"
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE

        val titleParam = if (!isCategory) query else null
        val categoryParam = if (isCategory) query else null

        Log.d(
            "DEBUG_FLOW",
            "API 호출 파라미터 → title=$titleParam | category=$categoryParam | sort=$sort"
        )

        RetrofitClient.getApiService().getProductLists(
            title = titleParam,
            category = categoryParam,
            sort = sort
        ).enqueue(object : Callback<MsgEntity> {

            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {

                Log.d("DEBUG_FLOW", "API 응답 도착. 성공 여부=${response.isSuccessful}")

                binding.progressBar.visibility = View.GONE

                if (!response.isSuccessful) {
                    Log.e(
                        "DEBUG_FLOW",
                        "❌ API 실패: code=${response.code()} | body=${response.errorBody()?.string()}"
                    )
                    binding.emptyText.visibility = View.VISIBLE
                    adapter.updateList(emptyList())
                    return
                }

                val rawData = response.body()?.data
                Log.d("DEBUG_FLOW", "rawData=$rawData")

                if (rawData == null) {
                    Log.e("DEBUG_FLOW", "❌ rawData=null (서버 문제 가능)")
                    binding.emptyText.visibility = View.VISIBLE
                    adapter.updateList(emptyList())
                    return
                }

                try {
                    val gson = Gson()
                    val listType = object : TypeToken<List<ProductListDTO>>() {}.type
                    val json = gson.toJson(rawData)

                    Log.d("DEBUG_FLOW", "rawData JSON=$json")

                    val productList: List<ProductListDTO> = gson.fromJson(json, listType)

                    Log.d("DEBUG_FLOW", "파싱된 productList size=${productList.size}")

                    if (productList.isEmpty()) {
                        binding.emptyText.visibility = View.VISIBLE
                        adapter.updateList(emptyList())
                    } else {
                        adapter.updateList(productList)
                        binding.emptyText.visibility = View.GONE
                    }

                } catch (e: Exception) {
                    Log.e("DEBUG_FLOW", "❌ JSON 파싱 오류", e)
                    binding.emptyText.visibility = View.VISIBLE
                    adapter.updateList(emptyList())
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("DEBUG_FLOW", "❌ 네트워크 실패", t)
                binding.progressBar.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
                adapter.updateList(emptyList())
            }
        })
    }
}
