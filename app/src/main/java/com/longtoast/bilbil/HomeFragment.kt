package com.longtoast.bilbil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.adapter.PopularSearchAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.SearchHistoryDTO
import com.longtoast.bilbil.dto.PopularSearchDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.dto.MemberDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var popularAdapter: PopularSearchAdapter

    override fun onResume() {
        super.onResume()
        Log.d("MY_LOCATION", "HomeFragment.onResume → 내 위치 새로 로드")
        loadMyLocation()

        Log.d("SEARCH_HISTORY", "HomeFragment.onResume → 최근 검색어 새로 로드")
        loadSearchHistory()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("DEBUG_FLOW", "HomeFragment.onViewCreated() 실행됨")

        setupSearchBar()
        setupCategoryRecycler()
        setupPopularRecycler()


    }

    private fun loadMyLocation() {
        RetrofitClient.getApiService().getMyInfo()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) return

                    val raw = response.body()?.data ?: return

                    try {
                        val gson = Gson()
                        val type = object : TypeToken<MemberDTO>() {}.type
                        val member: MemberDTO = gson.fromJson(gson.toJson(raw), type)

                        // ⭐ 주소 표시
                        val address = member.address ?: "내 위치"
                        binding.locationText.text = address

                        // ⭐ 프로필 이미지 표시 (중요)
                        val imageUrl = member.profileImageUrl
                        if (!imageUrl.isNullOrEmpty()) {

                            // 서버에서 넘긴 URL이 "/uploads/..." 이므로 절대 URL 만들기
                            val fullUrl =
                                if (imageUrl.startsWith("http")) imageUrl
                                else ServerConfig.HTTP_BASE_URL + imageUrl.replaceFirst("/", "")

                            // XML의 location_icon 에 프로필 이미지 적용
                            Glide.with(requireContext())
                                .load(fullUrl)
                                .circleCrop()
                                .into(binding.locationIcon)
                        }

                    } catch (e: Exception) {
                        Log.e("MY_INFO", "MemberDTO 파싱오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("MY_INFO", "내 위치/프로필 불러오기 실패", t)
                }
            })
    }

    // 🔍 검색 바 설정
    private fun setupSearchBar() {
        binding.searchBar.apply {
            // 기본 SearchView 모양 유지 (아이콘 + 힌트 + X 버튼)
            setIconifiedByDefault(true)
            queryHint = "근처 물건을 검색해 보세요"

            // ✅ 1) 검색창 아무 곳이나 탭하면 활성화 + 인기검색어 열기
            setOnClickListener {
                // 접혀있으면 펼치고
                if (isIconified) {
                    setIconified(false)
                }
                // 포커스 주고
                requestFocus()
                // 인기 검색어 보여주기 + 로드
                togglePopularList(true)
                loadPopularSearches()
            }

            // ✅ 2) X 버튼/닫기 눌러서 '접을' 때
            setOnCloseListener {
                togglePopularList(false)
                false   // false: 기본 동작(접기)도 같이 실행
            }

            // ✅ 3) 바깥 터치해서 포커스 잃으면 → 검색창/리스트 둘 다 접기
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    togglePopularList(false)
                    if (!isIconified) {
                        setIconified(true)
                    }
                }
            }

            // ✅ 4) 키보드의 검색 버튼 눌렀을 때
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val keyword = query?.trim().orEmpty()
                    if (keyword.isNotEmpty()) {
                        moveToSearchResult(keyword, isCategory = false)
                        clearFocus()
                        togglePopularList(false)
                        if (!isIconified) {
                            setIconified(true)
                        }
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return false
                }
            })
        }

        // 🔥 바깥(스크롤 영역)을 터치하면 검색창 포커스 제거 → 위 FocusChangeListener가 처리
        binding.scrollView.setOnTouchListener { _, _ ->
            if (binding.searchBar.hasFocus()) {
                binding.searchBar.clearFocus()
            }
            false
        }
    }

    // 검색결과 화면으로 이동
    private fun moveToSearchResult(keyword: String, isCategory: Boolean) {
        val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
            putExtra("SEARCH_QUERY", keyword)
            putExtra("SEARCH_IS_CATEGORY", isCategory)
        }
        Log.d("DEBUG_FLOW", "SearchResultActivity 이동 → query=$keyword | isCategory=$isCategory")
        startActivity(intent)
    }

    // 카테고리 RecyclerView
    private fun setupCategoryRecycler() {
        val categoryList = listOf("자전거", "가구", "캠핑", "전자제품", "운동", "의류")

        Log.d("DEBUG_FLOW", "카테고리 리스트 로드 완료: $categoryList")

        binding.categoryRecyclerView.layoutManager =
            GridLayoutManager(requireContext(), 3)

        binding.categoryRecyclerView.adapter =
            CategoryAdapter(categoryList) { categoryName ->
                Log.d("DEBUG_FLOW", "카테고리 클릭됨 → $categoryName")
                moveToSearchResult(categoryName, isCategory = true)
            }
    }

    // 🔍 검색창 아래에 표시할 인기 검색어 리스트용 RecyclerView
    private fun setupPopularRecycler() {
        popularAdapter = PopularSearchAdapter(emptyList()) { keyword ->
            Log.d("POPULAR_SEARCH", "인기 검색어 클릭 → $keyword")
            moveToSearchResult(keyword, isCategory = false)
            binding.searchBar.setQuery(keyword, false)
            binding.searchBar.clearFocus()
            togglePopularList(false)
        }

        binding.popularRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = popularAdapter
            visibility = View.GONE
        }
    }

    private fun togglePopularList(show: Boolean) {
        binding.popularRecyclerView.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ⭐ 전역 인기 검색어 (검색창 클릭 시 아래 리스트로 표시)
    private fun loadPopularSearches() {
        Log.d("POPULAR_SEARCH", "인기 검색어 불러오기 시작")

        RetrofitClient.getApiService().getPopularSearches()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Log.e(
                            "POPULAR_SEARCH",
                            "API 실패: code=${response.code()} | body=${response.errorBody()?.string()}"
                        )
                        togglePopularList(false)
                        return
                    }

                    val rawData = response.body()?.data
                    Log.d("POPULAR_SEARCH", "rawData=$rawData")

                    if (rawData == null) {
                        Log.e("POPULAR_SEARCH", "rawData=null")
                        togglePopularList(false)
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<PopularSearchDTO>>() {}.type
                        val json = gson.toJson(rawData)

                        Log.d("POPULAR_SEARCH", "rawData JSON=$json")

                        val popularList: List<PopularSearchDTO> = gson.fromJson(json, listType)

                        if (popularList.isEmpty()) {
                            Log.d("POPULAR_SEARCH", "인기 검색어 없음")
                            togglePopularList(false)
                            return
                        }

                        Log.d("POPULAR_SEARCH", "인기 검색어 개수=${popularList.size}")
                        popularAdapter.updateList(popularList)
                        togglePopularList(true)
                    } catch (e: Exception) {
                        Log.e("POPULAR_SEARCH", "JSON 파싱 오류", e)
                        togglePopularList(false)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("POPULAR_SEARCH", "네트워크 실패", t)
                    togglePopularList(false)
                }
            })
    }

    // ⭐ 내가 전에 검색했던 검색어 (최근 검색어) → Chip 으로 표시
    private fun loadSearchHistory() {
        Log.d("SEARCH_HISTORY", "최근 검색어 불러오기 시작")

        RetrofitClient.getApiService().getMySearchHistory()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Log.e(
                            "SEARCH_HISTORY",
                            "API 실패: code=${response.code()} | body=${response.errorBody()?.string()}"
                        )
                        return
                    }

                    val rawData = response.body()?.data
                    Log.d("SEARCH_HISTORY", "rawData=$rawData")

                    if (rawData == null) {
                        Log.e("SEARCH_HISTORY", "rawData=null")
                        renderHistoryChips(emptyList())
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<SearchHistoryDTO>>() {}.type
                        val json = gson.toJson(rawData)

                        Log.d("SEARCH_HISTORY", "rawData JSON=$json")

                        val historyList: List<SearchHistoryDTO> =
                            gson.fromJson(json, listType)

                        renderHistoryChips(historyList)
                    } catch (e: Exception) {
                        Log.e("SEARCH_HISTORY", "JSON 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("SEARCH_HISTORY", "네트워크 실패", t)
                }
            })
    }

    private fun renderHistoryChips(historyList: List<SearchHistoryDTO>) {
        val chipGroup = binding.chipGroupPopular
        chipGroup.removeAllViews()

        if (historyList.isEmpty()) {
            return
        }

        for (item in historyList) {
            val chip = Chip(requireContext()).apply {
                text = item.keyword
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    Log.d("SEARCH_HISTORY", "최근 검색어 클릭 → ${item.keyword}")
                    moveToSearchResult(item.keyword, isCategory = false)
                }
            }
            chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
