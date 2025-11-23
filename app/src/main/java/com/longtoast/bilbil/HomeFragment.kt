package com.longtoast.bilbil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.PopularSearchDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.drawerlayout.widget.DrawerLayout

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        loadPopularSearches()
    }

    // 🔍 검색 바 설정
    private fun setupSearchBar() {
        val searchEditTextId = binding.searchBar.context.resources
            .getIdentifier("search_src_text", "id", binding.searchBar.context.packageName)

        val searchEditText = binding.searchBar.findViewById<EditText>(searchEditTextId)

        searchEditText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchEditText.setSingleLine(true)

        binding.searchBar.setOnClickListener {
            binding.searchBar.isIconified = false
            binding.searchBar.requestFocus()
            searchEditText.requestFocus()

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val query = binding.searchBar.query.toString()
                Log.d("DEBUG_FLOW", "Enter 감지! 검색 실행 → $query")

                if (query.isNotEmpty()) {
                    moveToSearchResult(query, isCategory = false)
                    binding.searchBar.clearFocus()
                }
                true
            } else {
                false
            }
        }

        binding.searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
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

    // ⭐ 요즘 많이 찾는 검색어
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
                        return
                    }

                    val rawData = response.body()?.data
                    Log.d("POPULAR_SEARCH", "rawData=$rawData")

                    if (rawData == null) {
                        Log.e("POPULAR_SEARCH", "rawData=null")
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
                            return
                        }

                        Log.d("POPULAR_SEARCH", "인기 검색어 개수=${popularList.size}")
                        renderPopularChips(popularList)
                    } catch (e: Exception) {
                        Log.e("POPULAR_SEARCH", "JSON 파싱 오류", e)
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("POPULAR_SEARCH", "네트워크 실패", t)
                }
            })
    }

    private fun renderPopularChips(popularList: List<PopularSearchDTO>) {
        val chipGroup = binding.chipGroupPopular
        chipGroup.removeAllViews()

        for (item in popularList) {
            val chip = Chip(requireContext()).apply {
                text = item.keyword
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    Log.d("POPULAR_SEARCH", "인기 검색어 클릭 → ${item.keyword}")
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
