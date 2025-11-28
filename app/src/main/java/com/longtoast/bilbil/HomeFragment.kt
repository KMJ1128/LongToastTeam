package com.longtoast.bilbil

import androidx.core.view.isVisible
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.adapter.PopularSearchAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.PopularSearchDTO
import com.longtoast.bilbil.dto.ProductListDTO
import com.longtoast.bilbil.dto.SearchHistoryDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.widget.Toast
import com.longtoast.bilbil.ProductAdapter
import com.longtoast.bilbil.ProductDetailActivity
// 💡 ImageUrlUtils import 추가
import com.longtoast.bilbil.ImageUrlUtils

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var popularAdapter: PopularSearchAdapter
    private lateinit var productAdapter: ProductAdapter
    private lateinit var productLayoutManager: RecyclerView.LayoutManager

    override fun onResume() {
        super.onResume()
        loadMyLocation()
        loadSearchHistory()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchBar()
        setupCategoryRecycler()
        setupPopularRecycler()
        setupProductRecycler()
        loadProducts()

        // 🆕 메뉴 버튼 클릭 리스너 추가
        binding.menuButton.setOnClickListener {
            (activity as? HomeHostActivity)?.openDrawer()
        }
    }

    /** 🔵 사용자 주소 및 프로필 이미지 로드 */
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

                        binding.locationText.text = member.address ?: "내 위치"

                        // 💡 프로필 이미지 로드 로직 수정: location_icon 대신 profileImage 사용
                        val imageUrl = member.profileImageUrl
                        if (!imageUrl.isNullOrEmpty()) {
                            // 💡 ImageUrlUtils 사용으로 변경
                            val fullUrl = ImageUrlUtils.resolve(imageUrl)

                            Glide.with(requireContext())
                                .load(fullUrl)
                                .circleCrop()
                                .into(binding.profileImage) // 💡 profileImage ID로 로드
                        }
                        // 💡 location_icon에는 위치 아이콘이 고정되어 있으므로,
                        // 프로필 이미지 로직은 profileImage에만 적용하고 locationIcon 로직은 제거합니다.

                    } catch (e: Exception) {
                        Log.e("MY_INFO", "MemberDTO 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("MY_INFO", "내 위치/프로필 불러오기 실패", t)
                }
            })
    }

    /** 🔍 검색 바 설정 */
    private fun setupSearchBar() {
        binding.searchBar.apply {
            setIconifiedByDefault(true)
            queryHint = "근처 물건을 검색해 보세요"

            setOnClickListener {
                if (isIconified) setIconified(false)
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
                    if (!isIconified) setIconified(true)
                }
            }

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    val keyword = query?.trim().orEmpty()
                    if (keyword.isNotEmpty()) {
                        moveToSearchResult(keyword, false)
                        clearFocus()
                        togglePopularList(false)
                        if (!isIconified) setIconified(true)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean = false
            })
        }

        binding.scrollView.setOnTouchListener { _, _ ->
            if (binding.searchBar.hasFocus()) binding.searchBar.clearFocus()
            false
        }
    }

    /** 🔵 검색 결과 화면으로 이동 */
    private fun moveToSearchResult(keyword: String, isCategory: Boolean) {
        val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
            putExtra("SEARCH_QUERY", keyword)
            putExtra("SEARCH_IS_CATEGORY", isCategory)
        }
        startActivity(intent)
    }

    /** 🔵 카테고리 RecyclerView */
    private fun setupCategoryRecycler() {
        val categoryList = listOf("자전거", "가구", "캠핑", "전자제품", "운동", "의류")

        binding.categoryRecyclerView.layoutManager =
            GridLayoutManager(requireContext(), 3)

        binding.categoryRecyclerView.adapter =
            CategoryAdapter(categoryList) { categoryName ->
                moveToSearchResult(categoryName, true)
            }
    }

    /** 🔵 인기 검색어 RecyclerView */
    private fun setupPopularRecycler() {
        popularAdapter = PopularSearchAdapter(emptyList()) { keyword ->
            moveToSearchResult(keyword, false)
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

    /** 🔵 전체 상품 RecyclerView */
    private fun setupProductRecycler() {
        productLayoutManager = LinearLayoutManager(requireContext())

        productAdapter = ProductAdapter(emptyList()) { itemId ->
            val intent = Intent(requireContext(), ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", itemId)
            }
            startActivity(intent)
        }

        binding.recyclerProducts.apply {
            layoutManager = productLayoutManager
            adapter = productAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun togglePopularList(show: Boolean) {
        binding.popularRecyclerView.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** 🔵 전체 상품 불러오기 */
    private fun loadProducts() {
        binding.productsProgress.isVisible = true
        binding.textProductsEmpty.isVisible = false

        RetrofitClient.getApiService().getProductLists()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    binding.productsProgress.isVisible = false

                    if (!response.isSuccessful) {
                        binding.textProductsEmpty.isVisible = true
                        Toast.makeText(requireContext(), "상품을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val rawData = response.body()?.data ?: run {
                        binding.textProductsEmpty.isVisible = true
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<ProductListDTO>>() {}.type
                        val productList: List<ProductListDTO> =
                            gson.fromJson(gson.toJson(rawData), listType)

                        if (productList.isEmpty()) {
                            binding.textProductsEmpty.isVisible = true
                        } else {
                            binding.textProductsEmpty.isVisible = false
                            productAdapter.updateList(productList)
                        }
                    } catch (e: Exception) {
                        binding.textProductsEmpty.isVisible = true
                        Log.e("HOME_PRODUCTS", "상품 목록 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    binding.productsProgress.isVisible = false
                    binding.textProductsEmpty.isVisible = true
                }
            })
    }

    /** 🔵 전역 인기 검색어 */
    private fun loadPopularSearches() {
        RetrofitClient.getApiService().getPopularSearches()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        togglePopularList(false)
                        return
                    }

                    val rawData = response.body()?.data ?: run {
                        togglePopularList(false)
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<PopularSearchDTO>>() {}.type
                        val popularList: List<PopularSearchDTO> =
                            gson.fromJson(gson.toJson(rawData), listType)

                        if (popularList.isEmpty()) {
                            togglePopularList(false)
                        } else {
                            popularAdapter.updateList(popularList)
                            togglePopularList(true)
                        }
                    } catch (e: Exception) {
                        togglePopularList(false)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    togglePopularList(false)
                }
            })
    }

    /** 🔵 최근 검색어 불러오기 */
    private fun loadSearchHistory() {
        RetrofitClient.getApiService().getMySearchHistory()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) return

                    val rawData = response.body()?.data ?: run {
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
                        Log.e("SEARCH_HISTORY", "파싱 오류", e)
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

        if (historyList.isEmpty()) return

        for (item in historyList) {
            val chip = Chip(requireContext()).apply {
                text = item.keyword
                isCheckable = false
                setOnClickListener {
                    moveToSearchResult(item.keyword, false)
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