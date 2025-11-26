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
import com.google.android.material.chip.Chip
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.adapter.PopularSearchAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.SearchHistoryDTO
import com.longtoast.bilbil.dto.PopularSearchDTO
import com.longtoast.bilbil.dto.ProductListDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.drawerlayout.widget.DrawerLayout

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var popularAdapter: PopularSearchAdapter
    private lateinit var nearbyItemsAdapter: ProductAdapter  // 🆕

    override fun onResume() {
        super.onResume()
        Log.d("SEARCH_HISTORY", "HomeFragment.onResume → 최근 검색어 새로 로드")
        loadSearchHistory()
        loadNearbyItems()  // 🆕 내 지역 물품 로드
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

        setupMenuButton()
        setupSearchBar()
        setupCategoryRecycler()
        setupPopularRecycler()
        setupNearbyItemsRecycler()  // 🆕
        updateLocationText()  // 🆕
    }

    // 🆕 위치 텍스트 업데이트
    private fun updateLocationText() {
        val address = AuthTokenManager.getAddress()
        if (address != null) {
            // "서울특별시 강남구 역삼동" → "역삼동"으로 간단하게
            val shortAddress = address.split(" ").lastOrNull() ?: address
            binding.locationText.text = shortAddress
        } else {
            binding.locationText.text = "내 위치"
        }
    }

    // 🆕 내 지역 물품 RecyclerView 설정
    private fun setupNearbyItemsRecycler() {
        nearbyItemsAdapter = ProductAdapter(emptyList()) { itemId ->
            val intent = Intent(requireContext(), ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", itemId)
            }
            startActivity(intent)
        }

        binding.nearbyItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nearbyItemsAdapter
        }
    }

    // 🆕 내 지역 물품 로드
    private fun loadNearbyItems() {
        val myAddress = AuthTokenManager.getAddress()

        if (myAddress == null) {
            Log.d("NEARBY_ITEMS", "주소 정보 없음")
            binding.nearbyEmptyText.visibility = View.VISIBLE
            binding.nearbyEmptyText.text = "위치 정보를 설정해주세요"
            return
        }

        Log.d("NEARBY_ITEMS", "내 지역 물품 로드 시작: $myAddress")

        binding.nearbyProgressBar.visibility = View.VISIBLE
        binding.nearbyEmptyText.visibility = View.GONE

        // API 호출: 내 주소와 같은 지역의 물품만 가져오기
        RetrofitClient.getApiService().getProductLists(
            title = null,
            category = null,
            sort = null
        ).enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                binding.nearbyProgressBar.visibility = View.GONE

                if (!response.isSuccessful) {
                    Log.e("NEARBY_ITEMS", "API 실패: ${response.code()}")
                    showEmptyState()
                    return
                }

                val rawData = response.body()?.data
                if (rawData == null) {
                    Log.e("NEARBY_ITEMS", "rawData=null")
                    showEmptyState()
                    return
                }

                try {
                    val gson = Gson()
                    val listType = object : TypeToken<List<ProductListDTO>>() {}.type
                    val allProducts: List<ProductListDTO> = gson.fromJson(gson.toJson(rawData), listType)

                    // 🔍 내 주소와 같은 지역의 물품만 필터링
                    val nearbyProducts = allProducts.filter { product ->
                        product.address?.contains(myAddress) == true ||
                                myAddress.contains(product.address ?: "")
                    }

                    Log.d("NEARBY_ITEMS", "전체: ${allProducts.size}, 내 지역: ${nearbyProducts.size}")

                    if (nearbyProducts.isEmpty()) {
                        showEmptyState()
                    } else {
                        nearbyItemsAdapter.updateList(nearbyProducts)
                        binding.nearbyEmptyText.visibility = View.GONE
                    }

                } catch (e: Exception) {
                    Log.e("NEARBY_ITEMS", "JSON 파싱 오류", e)
                    showEmptyState()
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("NEARBY_ITEMS", "네트워크 실패", t)
                binding.nearbyProgressBar.visibility = View.GONE
                showEmptyState()
            }
        })
    }

    // 🆕 빈 상태 표시
    private fun showEmptyState() {
        binding.nearbyEmptyText.visibility = View.VISIBLE
        binding.nearbyEmptyText.text = "우리 동네에 등록된 물품이 없습니다"
    }

    // 검색 바 설정
    private fun setupSearchBar() {
        binding.searchBar.apply {
            setIconifiedByDefault(true)
            queryHint = "근처 물건을 검색해 보세요"

            setOnClickListener {
                if (isIconified) {
                    setIconified(false)
                }
                requestFocus()
                togglePopularList(true)
                loadPopularSearches()
            }

            setOnCloseListener {
                togglePopularList(false)
                false
            }

            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    togglePopularList(false)
                    if (!isIconified) {
                        setIconified(true)
                    }
                }
            }

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

        binding.scrollView.setOnTouchListener { _, _ ->
            if (binding.searchBar.hasFocus()) {
                binding.searchBar.clearFocus()
            }
            false
        }
    }

    private fun moveToSearchResult(keyword: String, isCategory: Boolean) {
        val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
            putExtra("SEARCH_QUERY", keyword)
            putExtra("SEARCH_IS_CATEGORY", isCategory)
        }
        Log.d("DEBUG_FLOW", "SearchResultActivity 이동 → query=$keyword | isCategory=$isCategory")
        startActivity(intent)
    }

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

    private fun loadPopularSearches() {
        Log.d("POPULAR_SEARCH", "인기 검색어 불러오기 시작")

        RetrofitClient.getApiService().getPopularSearches()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Log.e("POPULAR_SEARCH", "API 실패: code=${response.code()}")
                        togglePopularList(false)
                        return
                    }

                    val rawData = response.body()?.data
                    if (rawData == null) {
                        Log.e("POPULAR_SEARCH", "rawData=null")
                        togglePopularList(false)
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<PopularSearchDTO>>() {}.type
                        val json = gson.toJson(rawData)
                        val popularList: List<PopularSearchDTO> = gson.fromJson(json, listType)

                        if (popularList.isEmpty()) {
                            togglePopularList(false)
                            return
                        }

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

    private fun loadSearchHistory() {
        Log.d("SEARCH_HISTORY", "최근 검색어 불러오기 시작")

        RetrofitClient.getApiService().getMySearchHistory()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Log.e("SEARCH_HISTORY", "API 실패")
                        return
                    }

                    val rawData = response.body()?.data
                    if (rawData == null) {
                        renderHistoryChips(emptyList())
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<SearchHistoryDTO>>() {}.type
                        val historyList: List<SearchHistoryDTO> =
                            gson.fromJson(gson.toJson(rawData), listType)

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

    private fun setupMenuButton() {
        binding.menuButton.setOnClickListener {
            val drawerLayout = activity?.findViewById<DrawerLayout>(R.id.drawer_layout)
            drawerLayout?.openDrawer(androidx.core.view.GravityCompat.END)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}