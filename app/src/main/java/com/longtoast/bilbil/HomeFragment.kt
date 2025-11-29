package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 🔥 이제 이 어댑터는 "최근 검색어 리스트" 표시용
    private lateinit var popularAdapter: PopularSearchAdapter
    private lateinit var productAdapter: ProductAdapter
    private lateinit var productLayoutManager: RecyclerView.LayoutManager

    override fun onResume() {
        super.onResume()
        loadMyLocation()
        loadPopularSearches()   // 🔥 위쪽 Chip에 "인기 검색어" 채우기
        updateCartBadge()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 햄버거 메뉴
        binding.btnMenu.setOnClickListener {
            val drawerLayout = requireActivity().findViewById<DrawerLayout>(R.id.drawer_layout)
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.END)
            } else {
                Log.e("HomeFragment", "DrawerLayout을 찾을 수 없습니다.")
            }
        }

        // 장바구니 버튼
        binding.btnGoCart.setOnClickListener {
            val intent = Intent(requireContext(), CartActivity::class.java)
            startActivity(intent)
        }

        setupSearchBar()
        setupCategoryRecycler()
        setupPopularRecycler()
        setupProductRecycler()
        loadProducts()
        // 인기 검색어는 onResume에서 호출
    }

    // 🔥 장바구니 뱃지
    private fun updateCartBadge() {
        val count = CartManager.getItems().size
        if (count > 0) {
            binding.cartBadge.text = if (count > 99) "99+" else count.toString()
            binding.cartBadge.isVisible = true
        } else {
            binding.cartBadge.isVisible = false
        }
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

                        binding.locationText.text = member.address ?: "내 위치"
                        val fullUrl = ImageUrlUtils.resolve(member.profileImageUrl)
                        if (!fullUrl.isNullOrEmpty()) {
                            Glide.with(requireContext())
                                .load(fullUrl)
                                .circleCrop()
                                .into(binding.profileImage)
                        }
                    } catch (e: Exception) {
                        Log.e("MY_INFO", "MemberDTO 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("MY_INFO", "내 위치/프로필 불러오기 실패", t)
                }
            })
    }

    private fun setupSearchBar() {
        binding.searchBar.apply {
            setIconifiedByDefault(true)
            queryHint = "근처 물건을 검색해 보세요"

            setOnClickListener {
                if (isIconified) setIconified(false)
                requestFocus()
                // 🔥 검색창 클릭 시: 아래 리스트(RecyclerView)에 "최근 검색어" 표시
                togglePopularList(true)
                loadSearchHistory()
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

    private fun moveToSearchResult(keyword: String, isCategory: Boolean) {
        val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
            putExtra("SEARCH_QUERY", keyword)
            putExtra("SEARCH_IS_CATEGORY", isCategory)
        }
        startActivity(intent)
    }

    private fun setupCategoryRecycler() {
        val categoryList = listOf("자전거", "가구", "캠핑", "전자제품", "운동", "의류")
        binding.categoryRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.categoryRecyclerView.adapter = CategoryAdapter(categoryList) { categoryName ->
            moveToSearchResult(categoryName, true)
        }
    }

    // 🔥 여기 RecyclerView는 이제 "최근 검색어" 리스트용
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

    // 🔥 "인기 검색어" → 위쪽 ChipGroup 에 뿌리기
    private fun loadPopularSearches() {
        RetrofitClient.getApiService().getPopularSearches()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        renderPopularChips(emptyList())
                        return
                    }
                    val rawData = response.body()?.data ?: run {
                        renderPopularChips(emptyList())
                        return
                    }
                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<PopularSearchDTO>>() {}.type
                        val popularList: List<PopularSearchDTO> =
                            gson.fromJson(gson.toJson(rawData), listType)

                        renderPopularChips(popularList)
                    } catch (e: Exception) {
                        renderPopularChips(emptyList())
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    renderPopularChips(emptyList())
                }
            })
    }

    // ✅ 인기 검색어 → ChipGroup
    private fun renderPopularChips(popularList: List<PopularSearchDTO>) {
        val chipGroup = binding.chipGroupPopular
        chipGroup.removeAllViews()

        if (popularList.isEmpty()) return

        for (item in popularList) {
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

    // ✅ 최근 검색어 → RecyclerView 리스트(popularRecyclerView)
    private fun loadSearchHistory() {
        RetrofitClient.getApiService().getMySearchHistory()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        popularAdapter.updateList(emptyList())
                        return
                    }

                    val rawData = response.body()?.data ?: run {
                        popularAdapter.updateList(emptyList())
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<SearchHistoryDTO>>() {}.type
                        val historyList: List<SearchHistoryDTO> =
                            gson.fromJson(gson.toJson(rawData), listType)

                        val keywords = historyList.map { it.keyword }
                        popularAdapter.updateList(keywords)
                    } catch (e: Exception) {
                        Log.e("SEARCH_HISTORY", "파싱 오류", e)
                        popularAdapter.updateList(emptyList())
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("SEARCH_HISTORY", "네트워크 실패", t)
                    popularAdapter.updateList(emptyList())
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
