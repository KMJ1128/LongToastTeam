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

    // 정렬 필터 상태
    private var filterLatest: Boolean = true       // 기본: 최신순 ON
    private var filterLowPrice: Boolean = false   // 기본: 가격 낮은 순 OFF

    // ✅ 지역 필터 상태 (RegionSelectionActivity에서 선택한 전체 문자열)
    private var currentRegionFilter: String? = null

    // 서버에서 받아온 원본 리스트
    private var originalProductList: List<ProductListDTO> = emptyList()

    // ✅ 지역 선택 화면(RegionSelectionActivity)에서 결과 받아오기
    private val regionFilterLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selectedAddress = result.data!!.getStringExtra("FINAL_ADDRESS")
                Log.d("REGION_FILTER", "선택된 주소: $selectedAddress")

                if (!selectedAddress.isNullOrBlank()) {
                    currentRegionFilter = selectedAddress
                    binding.btnRegionFilter.text = selectedAddress   // 버튼에는 전체 주소 표시
                } else {
                    currentRegionFilter = null
                    binding.btnRegionFilter.text = "지역 전체"
                }

                applySortAndFilter()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DEBUG_FLOW", "🔥 SearchResultActivity.onCreate() 실행됨")

        binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupDrawerMenu()
        setupRecycler()
        setupFilterButtons()
        setupRegionFilterButton()   // ✅ 지역 버튼 세팅

        // 전달된 검색 값 확인
        var query = intent.getStringExtra("SEARCH_QUERY")
        isCategory = intent.getBooleanExtra("SEARCH_IS_CATEGORY", false)

        Log.d("DEBUG_FLOW", "전달 받은 원본 검색 정보 → query=$query, isCategory=$isCategory")

        // "#:{category}" 형태면 카테고리 검색 모드로 전환
        if (!query.isNullOrBlank() && query.startsWith("#:")) {
            isCategory = true
            query = query.removePrefix("#:").trim()
            Log.d("DEBUG_FLOW", "파싱 후 → query=$query | isCategory=$isCategory (카테고리 검색 모드)")
        }

        if (query == null) {
            Log.e("DEBUG_FLOW", "❌ query=null → SearchResultActivity 오류 가능")
        }

        currentQuery = query

        // 헤더 검색창 세팅
        setupSearchBar(currentQuery ?: "")

        // 상단 "{검색어} 검색 결과" 텍스트
        binding.queryText.text = if (isCategory) {
            "\"$query\" 카테고리"
        } else {
            "\"$query\" 검색 결과"
        }

        // 첫 로딩: 기본 필터(최신순 ON, 가격낮은순 OFF)로
        loadSearchResults(currentQuery, isCategory)

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
    // 헤더 세팅
    // -------------------------------------------------------------
    private fun setupHeader() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        binding.btnGoCart.setOnClickListener {
            val intent = Intent(this, CartActivity::class.java)
            startActivity(intent)
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    // 헤더 안 검색창 세팅
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
                        isCategory = false

                        // 새 검색 시 필터 상태 초기화
                        filterLatest = true
                        filterLowPrice = false
                        currentRegionFilter = null
                        binding.btnRegionFilter.text = "지역 전체"

                        binding.btnFilterLatest.isChecked = true
                        binding.btnFilterLowPrice.isChecked = false

                        binding.queryText.text = "\"$keyword\" 검색 결과"

                        loadSearchResults(currentQuery, isCategory)
                        clearFocus()
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            })
        }
    }

    // 헤더용 내 위치 + 상단 프로필
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
    // Drawer + NavigationView
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
                }

                R.id.nav_received_reviews -> {
                    Toast.makeText(this, "내가 받은 리뷰", Toast.LENGTH_SHORT).show()
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
    // 정렬 필터 버튼 (최신순, 가격 낮은 순)
    // -------------------------------------------------------------
    private fun setupFilterButtons() {
        binding.btnFilterLatest.text = "최신순"
        binding.btnFilterLowPrice.text = "가격 낮은 순"

        binding.btnFilterLatest.isCheckable = true
        binding.btnFilterLowPrice.isCheckable = true

        binding.btnFilterLatest.isChecked = filterLatest
        binding.btnFilterLowPrice.isChecked = filterLowPrice

        binding.btnFilterLatest.setOnClickListener {
            filterLatest = binding.btnFilterLatest.isChecked
            applySortAndFilter()
        }

        binding.btnFilterLowPrice.setOnClickListener {
            filterLowPrice = binding.btnFilterLowPrice.isChecked
            applySortAndFilter()
        }
    }

    // -------------------------------------------------------------
    // ✅ 지역 필터 버튼 (RegionSelectionActivity 띄우기)
    // -------------------------------------------------------------
    private fun setupRegionFilterButton() {
        binding.btnRegionFilter.text = "지역 전체"

        binding.btnRegionFilter.setOnClickListener {
            val intent = Intent(this, RegionSelectionActivity::class.java).apply {
                putExtra("MODE", "FILTER")  // 선택 모드 구분용
            }
            regionFilterLauncher.launch(intent)
        }
    }

    // -------------------------------------------------------------
    // 서버 통신: 검색 결과
    // -------------------------------------------------------------
    private fun loadSearchResults(query: String?, isCategory: Boolean) {

        Log.d(
            "DEBUG_FLOW",
            "loadSearchResults() 호출됨 / query=$query, isCategory=$isCategory"
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE

        // 새 검색 시 지역 필터 초기화
        currentRegionFilter = null
        binding.btnRegionFilter.text = "지역 전체"

        val titleParam = if (!isCategory) query else null
        val categoryParam = if (isCategory) query else null

        Log.d(
            "DEBUG_FLOW",
            "API 호출 파라미터 → title=$titleParam | category=$categoryParam | sort=null(클라이언트 정렬)"
        )

        RetrofitClient.getApiService().getProductLists(
            title = titleParam,
            category = categoryParam,
            sort = null     // 정렬은 클라이언트에서 처리
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
                    originalProductList = emptyList()
                    adapter.updateList(emptyList())
                    return
                }

                val rawData = response.body()?.data
                Log.d("DEBUG_FLOW", "rawData=$rawData")

                if (rawData == null) {
                    Log.e("DEBUG_FLOW", "❌ rawData=null (서버 문제 가능)")
                    binding.emptyText.visibility = View.VISIBLE
                    originalProductList = emptyList()
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

                    originalProductList = productList

                    // 🔥 정렬 + 지역 필터 적용
                    applySortAndFilter()

                } catch (e: Exception) {
                    Log.e("DEBUG_FLOW", "❌ JSON 파싱 오류", e)
                    binding.emptyText.visibility = View.VISIBLE
                    originalProductList = emptyList()
                    adapter.updateList(emptyList())
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("DEBUG_FLOW", "❌ 네트워크 실패", t)
                binding.progressBar.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
                originalProductList = emptyList()
                adapter.updateList(emptyList())
            }
        })
    }

    // -------------------------------------------------------------
    // ✅ 선택된 주소 문자열에서 "구/군" 추출
    //   예)
    //    - "서울특별시 양천구 목4동"        → "양천구"
    //    - "경기도 수원시 장안구 조원동"    → "장안구"
    //    - "부산광역시 부산진구 가야동"     → "부산진구"
    // -------------------------------------------------------------
    private fun extractGuFromSelection(selection: String?): String? {
        if (selection.isNullOrBlank()) return null
        val parts = selection.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return null

        // 1) "구" 또는 "군"으로 끝나는 토큰 우선 탐색
        for (token in parts) {
            if (token.endsWith("구") || token.endsWith("군")) {
                return token
            }
        }

        // 2) 그래도 없으면 2번째 토큰 정도를 예비로 사용 (서울 종로구 처럼 "특별시" 다음)
        return parts.getOrNull(1)
    }

    // -------------------------------------------------------------
    // 정렬 + 지역 필터 적용
    // -------------------------------------------------------------
    private fun applySortAndFilter() {
        if (originalProductList.isEmpty()) {
            adapter.updateList(emptyList())
            binding.emptyText.visibility = View.VISIBLE
            return
        }

        var list = originalProductList

        // 1) ✅ 지역 필터 적용 (선택 주소에서 구/군만 뽑아서, 물품 주소에 contains)
        currentRegionFilter?.let { selection ->
            val guKeyword = extractGuFromSelection(selection)

            if (!guKeyword.isNullOrBlank()) {
                list = list.filter { p ->
                    val addr = p.address ?: return@filter false
                    addr.contains(guKeyword)
                }
            }
        }

        if (list.isEmpty()) {
            adapter.updateList(emptyList())
            binding.emptyText.visibility = View.VISIBLE
            return
        } else {
            binding.emptyText.visibility = View.GONE
        }

        // 2) 정렬 필터 적용
        if (!filterLatest && !filterLowPrice) {
            adapter.updateList(list)
            return
        }

        val sorted = list.sortedWith { a, b ->
            var cmp = 0

            // 최신순: id 내림차순(최근 등록된 상품일수록 id가 크다고 가정)
            if (filterLatest) {
                val ca = a.id
                val cb = b.id
                cmp = cb.compareTo(ca) // 큰 id(=최근)가 앞으로
            }

            // 가격 낮은 순 – 최신순에서 동률이면 2차 기준
            if (cmp == 0 && filterLowPrice) {
                val pa = a.price ?: 0
                val pb = b.price ?: 0
                cmp = pa.compareTo(pb) // 가격 낮은 게 앞으로
            }

            cmp
        }

        adapter.updateList(sorted)
    }
}
