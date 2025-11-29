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
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
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

    // 정렬 상태를 정의하는 Enum
    private enum class TimeSort { LATEST, OLDEST }
    private enum class PriceSort { LOW, HIGH }
    // 기간 필터 Enum은 그대로 사용 (단위 필터로 역할 변경)
    private enum class PeriodFilter { DAY, MONTH, HOUR }

    // 💡 복합 정렬을 위한 새로운 상태 변수: null이면 비활성화, 값이 있으면 활성화된 정렬 모드
    private var timeSortMode: TimeSort? = TimeSort.LATEST    // 기본: 최신순 활성화
    private var priceSortMode: PriceSort? = null             // 기본: 가격순 비활성화
    private var currentPeriodFilter: PeriodFilter = PeriodFilter.DAY // 기본: 일 (price_unit=1)

    // 현재 검색 상태
    private var currentQuery: String? = null
    private var isCategory: Boolean = false

    // ✅ 지역 필터 상태
    private var currentRegionFilter: String? = null

    // 서버에서 받아온 원본 리스트
    private var originalProductList: List<ProductListDTO> = emptyList()

    // 색상 정의 (MaterialButton의 TextColor를 수동으로 변경해야 토글처럼 보임)
    private val colorActive: Int by lazy { ContextCompat.getColor(this, R.color.colorPrimary) }
    private val colorInactive: Int by lazy { ContextCompat.getColor(this, R.color.trust_text_secondary) }

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

    private val editProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadNavigationHeader()
                loadMyLocationForHeader()
                Toast.makeText(this, "프로필이 업데이트되었습니다", Toast.LENGTH_SHORT).show()
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
        setupPeriodFilterButton()
        setupRegionFilterButton()

        // 전달된 검색 값 확인
        var query = intent.getStringExtra("SEARCH_QUERY")
        isCategory = intent.getBooleanExtra("SEARCH_IS_CATEGORY", false)

        if (!query.isNullOrBlank() && query.startsWith("#:")) {
            isCategory = true
            query = query.removePrefix("#:").trim()
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

        // 첫 로딩: 기본 필터로
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

                        // 새 검색 시 필터 상태 초기화 (시간순=최신순, 가격순=OFF, 기간=일)
                        timeSortMode = TimeSort.LATEST
                        priceSortMode = null
                        currentPeriodFilter = PeriodFilter.DAY // 기본: 일 단위
                        currentRegionFilter = null

                        updateFilterButtonUI() // UI 업데이트
                        binding.btnFilterPeriod.text = "일"
                        binding.btnRegionFilter.text = "지역 전체"

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

    // 💡 헤더용 내 위치 + 상단 프로필
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

    // 💡 장바구니 뱃지 표시
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
    // 💡 내비게이션 드로어 헤더 로드
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

    // 💡 SharedPreferences에서 정보 로드
    private fun loadFromSharedPreferences() {
        val headerView = binding.navView.getHeaderView(0)
        val nicknameTextView = headerView.findViewById<TextView>(R.id.nav_header_nickname)
        val addressTextView = headerView.findViewById<TextView>(R.id.nav_header_address)

        val nickname = AuthTokenManager.getNickname()
        val address = AuthTokenManager.getAddress()

        nicknameTextView.text = nickname ?: "닉네임 미지정"
        addressTextView.text = address ?: "위치 미지정"
    }

    // 💡 드로어 메뉴 세팅
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
    // 💡 정렬 필터 버튼 (시간순, 가격순) - 복합 정렬 지원 로직
    // -------------------------------------------------------------
    // 버튼 외형을 상태에 맞춰 업데이트
    private fun updateButtonAppearance(button: MaterialButton, isActive: Boolean, activeText: String) {
        if (isActive) {
            button.text = activeText
            button.setTextColor(colorActive)
            button.setStrokeColorResource(R.color.colorPrimary) // 활성 시 테두리 색상
        } else {
            button.text = when (button.id) {
                R.id.btn_filter_latest -> "시간순"
                R.id.btn_filter_low_price -> "가격순"
                else -> ""
            }
            button.setTextColor(colorInactive)
            button.setStrokeColorResource(R.color.trust_text_secondary) // 비활성 시 테두리 색상
        }
    }

    private fun setupFilterButtons() {
        // 초기 UI 상태 설정
        updateFilterButtonUI()

        // 💡 시간순 토글 로직
        binding.btnFilterLatest.setOnClickListener {
            timeSortMode = when (timeSortMode) {
                TimeSort.LATEST -> TimeSort.OLDEST // 최신순 -> 오래된순
                TimeSort.OLDEST -> null          // 오래된순 -> 비활성화
                null -> TimeSort.LATEST          // 비활성화 -> 최신순
            }
            // 가격순 상태는 유지
            updateFilterButtonUI()
            applySortAndFilter()
        }

        // 💡 가격순 토글 로직
        binding.btnFilterLowPrice.setOnClickListener {
            priceSortMode = when (priceSortMode) {
                PriceSort.LOW -> PriceSort.HIGH  // 낮은순 -> 높은순
                PriceSort.HIGH -> null           // 높은순 -> 비활성화
                null -> PriceSort.LOW            // 비활성화 -> 낮은순
            }
            // 시간순 상태는 유지
            updateFilterButtonUI()
            applySortAndFilter()
        }
    }

    // 필터 버튼 UI를 현재 상태에 맞춰 업데이트하는 유틸리티
    private fun updateFilterButtonUI() {
        // 시간순 버튼 UI 업데이트
        val isTimeActive = timeSortMode != null
        val timeActiveText = when (timeSortMode) {
            TimeSort.LATEST -> "최신순"
            TimeSort.OLDEST -> "오래된순"
            else -> "시간순"
        }
        updateButtonAppearance(binding.btnFilterLatest, isTimeActive, timeActiveText)

        // 가격순 버튼 UI 업데이트
        val isPriceActive = priceSortMode != null
        val priceActiveText = when (priceSortMode) {
            PriceSort.LOW -> "가격낮은순"
            PriceSort.HIGH -> "가격높은순"
            else -> "가격순"
        }
        updateButtonAppearance(binding.btnFilterLowPrice, isPriceActive, priceActiveText)
    }

    // -------------------------------------------------------------
    // 💡 기간 필터 버튼 (일, 월, 시간) - [최종 수정: 가격 단위 필터로 사용]
    // -------------------------------------------------------------
    private fun setupPeriodFilterButton() {
        binding.btnFilterPeriod.text = "일" // 초기 텍스트: 일

        binding.btnFilterPeriod.setOnClickListener {
            val periods = arrayOf("일", "월", "시간")

            AlertDialog.Builder(this)
                .setTitle("가격 단위 필터 선택")
                .setItems(periods) { _, which ->
                    val selectedPeriod = periods[which]

                    binding.btnFilterPeriod.text = selectedPeriod

                    // PeriodFilter Enum 값은 그대로 사용, 서버에 보낼 때 가격 단위 키워드로 사용
                    currentPeriodFilter = when (selectedPeriod) {
                        "일" -> PeriodFilter.DAY
                        "월" -> PeriodFilter.MONTH
                        "시간" -> PeriodFilter.HOUR
                        else -> PeriodFilter.DAY
                    }

                    // 서버에 재요청: 서버는 이 파라미터를 price_unit으로 해석해야 함
                    loadSearchResults(currentQuery, isCategory)

                    Toast.makeText(this, "$selectedPeriod 단위로 가격 필터링 요청됨", Toast.LENGTH_SHORT).show()
                }
                .show()
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
    // 서버 통신: 검색 결과 (period 파라미터가 price_unit 필터로 사용됨)
    // -------------------------------------------------------------
    private fun loadSearchResults(query: String?, isCategory: Boolean) {

        Log.d(
            "DEBUG_FLOW",
            "loadSearchResults() 호출됨 / query=$query, isCategory=$isCategory"
        )

        binding.progressBar.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE

        // 새 검색 시 지역 필터 초기화
        if (query != currentQuery) {
            currentRegionFilter = null
            binding.btnRegionFilter.text = "지역 전체"
        }

        val titleParam = if (!isCategory) query else null
        val categoryParam = if (isCategory) query else null

        // 💡 periodParam을 서버가 price_unit 필터로 사용하도록 한국어 키워드 전송
        val periodParam = currentPeriodFilter.name.toLowerCase().run {
            when (this) {
                "day" -> "일"     // price_unit=1 (일)
                "month" -> "월"   // price_unit=2 (월)
                "hour" -> "시간"  // price_unit=3 (시간)
                else -> "일"
            }
        }

        Log.d(
            "DEBUG_FLOW",
            "API 호출 파라미터 → title=$titleParam | category=$categoryParam | sort=null(클라이언트 정렬) | period_AS_PRICE_UNIT=$periodParam"
        )

        // 💡 period 파라미터 전송
        RetrofitClient.getApiService().getProductLists(
            title = titleParam,
            category = categoryParam,
            sort = null,     // 정렬은 클라이언트에서 처리
            period = periodParam
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
    // 💡 정렬 + 지역 필터 적용 (복합 정렬 로직)
    // -------------------------------------------------------------
    private fun applySortAndFilter() {
        if (originalProductList.isEmpty()) {
            adapter.updateList(emptyList())
            binding.emptyText.visibility = View.VISIBLE
            return
        }

        var list = originalProductList

        // 1) 지역 필터 적용
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

        // 2) 💡 복합 정렬 필터 적용 (Comparator chaining 사용)

        // 정렬 기준 목록 (우선순위 순서대로)
        val comparators = mutableListOf<Comparator<ProductListDTO>>()

        // 1순위: 가격순 정렬 (가격순이 활성화된 경우)
        priceSortMode?.let { sort ->
            val priceComparator = Comparator<ProductListDTO> { a, b ->
                val pa = a.price ?: 0
                val pb = b.price ?: 0
                when (sort) {
                    PriceSort.LOW -> pa.compareTo(pb) // 낮은 가격이 앞으로 (오름차순)
                    PriceSort.HIGH -> pb.compareTo(pa) // 높은 가격이 앞으로 (내림차순)
                }
            }
            comparators.add(priceComparator)
        }

        // 2순위: 시간순 정렬 (시간순이 활성화된 경우)
        timeSortMode?.let { sort ->
            val timeComparator = Comparator<ProductListDTO> { a, b ->
                // id를 시간 기준으로 사용 (최근 등록된 상품일수록 id가 크다고 가정)
                val ca = a.id
                val cb = b.id
                when (sort) {
                    TimeSort.LATEST -> cb.compareTo(ca) // 큰 id(=최근)가 앞으로 (내림차순)
                    TimeSort.OLDEST -> ca.compareTo(cb) // 작은 id(=오래됨)가 앞으로 (오름차순)
                }
            }
            comparators.add(timeComparator)
        }

        // Comparator chaining을 사용하여 복합 정렬 적용
        val finalComparator: Comparator<ProductListDTO> = when (comparators.size) {
            0 -> Comparator { _, _ -> 0 } // 정렬 기준이 없으면 순서 유지
            1 -> comparators[0]
            else -> comparators.drop(1).fold(comparators[0]) { acc, comparator ->
                acc.thenComparing(comparator)
            }
        }

        // 정렬된 리스트 업데이트
        val sorted = list.sortedWith(finalComparator)
        adapter.updateList(sorted)
    }
}