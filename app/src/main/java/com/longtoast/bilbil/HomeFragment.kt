package com.longtoast.bilbil

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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.adapter.PopularSearchAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.PopularSearchDTO
import com.longtoast.bilbil.dto.SearchHistoryDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var popularAdapter: PopularSearchAdapter

    override fun onResume() {
        super.onResume()

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

        Log.d("MY_LOCATION", "HomeFragment.onResume → 내 위치 새로 로드")
        loadMyLocation()

        Log.d("SEARCH_HISTORY", "HomeFragment.onResume → 최근 검색어 새로 로드")
        loadSearchHistory()
    }

    // ----------------------------------------------------
    // ⭐ 내 정보(주소 & 프로필 이미지) 불러오기
    // ----------------------------------------------------
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

                        // ⭐ URL 정규화
                        val fullUrl = ImageUrlUtils.resolve(member.profileImageUrl)

                        Log.d("IMG_URL", "raw = ${member.profileImageUrl}")
                        Log.d("IMG_URL", "resolved = $fullUrl")

                        // ⭐ Glide로 프로필 로드
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

    // ----------------------------------------------------
    // 🔍 검색 바 설정
    // ----------------------------------------------------
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

                override fun onQueryTextChange(newText: String?) = false
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

    // ----------------------------------------------------
    // 카테고리 RecyclerView
    // ----------------------------------------------------
    private fun setupCategoryRecycler() {
        val categoryList = listOf("자전거", "가구", "캠핑", "전자제품", "운동", "의류")

        binding.categoryRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = CategoryAdapter(categoryList) {
                moveToSearchResult(it, true)
            }
        }
    }

    // ----------------------------------------------------
    // 🔍 인기 검색어 RecyclerView
    // ----------------------------------------------------
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

    private fun togglePopularList(show: Boolean) {
        binding.popularRecyclerView.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ----------------------------------------------------
    // ⭐ 인기 검색어 불러오기
    // ----------------------------------------------------
    private fun loadPopularSearches() {
        RetrofitClient.getApiService().getPopularSearches()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        togglePopularList(false)
                        return
                    }

                    val raw = response.body()?.data ?: return togglePopularList(false)

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<PopularSearchDTO>>() {}.type
                        val json = gson.toJson(raw)

                        val popularList: List<PopularSearchDTO> =
                            gson.fromJson(json, listType)

                        if (popularList.isEmpty()) {
                            togglePopularList(false); return
                        }

                        popularAdapter.updateList(popularList)
                        togglePopularList(true)

                    } catch (e: Exception) {
                        togglePopularList(false)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    togglePopularList(false)
                }
            })
    }

    // ----------------------------------------------------
    // ⭐ 최근 검색어 불러오기
    // ----------------------------------------------------
    private fun loadSearchHistory() {
        RetrofitClient.getApiService().getMySearchHistory()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) return

                    val raw = response.body()?.data
                    if (raw == null) {
                        renderHistoryChips(emptyList())
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<SearchHistoryDTO>>() {}.type
                        val json = gson.toJson(raw)

                        val historyList: List<SearchHistoryDTO> =
                            gson.fromJson(json, listType)

                        renderHistoryChips(historyList)

                    } catch (_: Exception) { }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) { }
            })
    }

    private fun renderHistoryChips(historyList: List<SearchHistoryDTO>) {
        val chipGroup = binding.chipGroupPopular
        chipGroup.removeAllViews()

        historyList.forEach { item ->
            val chip = Chip(requireContext()).apply {
                text = item.keyword
                isClickable = true
                setOnClickListener { moveToSearchResult(item.keyword, false) }
            }
            chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
