package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivitySearchResultBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductListDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchResultBinding
    private lateinit var adapter: ProductAdapter

    private enum class SortOption {
        LATEST, PRICE_LOW, PRICE_HIGH
    }

    private var currentSort: SortOption = SortOption.LATEST
    private var latestProducts: List<ProductListDTO> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("DEBUG_FLOW", "🔥 SearchResultActivity.onCreate() 실행됨")

        binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("DEBUG_FLOW", "UI 바인딩 완료")

        // 🧷 툴바 뒤로가기 버튼
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // 🧷 하단 돌아가기 버튼
        binding.backButton.setOnClickListener {
            finish()
        }

        adapter = ProductAdapter(emptyList()) { itemId ->
            Log.d("DEBUG_FLOW", "아이템 클릭됨 → itemId=$itemId")
            val intent = Intent(this, ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", itemId)
            }
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // 전달된 검색 값 확인
        val query = intent.getStringExtra("SEARCH_QUERY")
        val isCategory = intent.getBooleanExtra("SEARCH_IS_CATEGORY", false)

        setupSearchUi(query.orEmpty(), isCategory)

        Log.d("DEBUG_FLOW", "전달 받은 검색 정보 → query=$query | isCategory=$isCategory")

        if (query == null) {
            Log.e("DEBUG_FLOW", "❌ query=null → SearchResultActivity 오류 발생 가능!")
        }

        // API 호출
        loadSearchResults(query, isCategory)
    }

    private fun loadSearchResults(query: String?, isCategory: Boolean) {

        Log.d("DEBUG_FLOW", "loadSearchResults() 호출됨")

        binding.progressContainer.visibility = android.view.View.VISIBLE
        binding.loadingLottie.playAnimation()
        binding.emptyText.visibility = android.view.View.GONE

        val titleParam = if (!isCategory) query else null
        val categoryParam = if (isCategory) query else null

        Log.d("DEBUG_FLOW", "API 호출 파라미터 → title=$titleParam | category=$categoryParam")

        RetrofitClient.getApiService().getProductLists(
            title = titleParam,
            category = categoryParam,
            sort = null
        ).enqueue(object : Callback<MsgEntity> {

            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {

                Log.d("DEBUG_FLOW", "API 응답 도착. 성공 여부=${response.isSuccessful}")

                binding.progressContainer.visibility = android.view.View.GONE
                binding.loadingLottie.pauseAnimation()

                if (!response.isSuccessful) {
                    Log.e(
                        "DEBUG_FLOW",
                        "❌ API 실패: code=${response.code()} | body=${response.errorBody()?.string()}"
                    )
                    binding.emptyText.visibility = android.view.View.VISIBLE
                    return
                }

                val rawData = response.body()?.data
                Log.d("DEBUG_FLOW", "rawData=$rawData")

                if (rawData == null) {
                    Log.e("DEBUG_FLOW", "❌ rawData=null (서버 제 가능)")
                    binding.emptyText.visibility = android.view.View.VISIBLE
                    return
                }

                try {
                    val gson = Gson()
                    val listType = object : TypeToken<List<ProductListDTO>>() {}.type
                    val json = gson.toJson(rawData)

                    Log.d("DEBUG_FLOW", "rawData JSON=$json")

                    val productList: List<ProductListDTO> = gson.fromJson(json, listType)

                    Log.d("DEBUG_FLOW", "파싱된 productList size=${productList.size}")

                    latestProducts = productList
                    renderProducts()

                } catch (e: Exception) {
                    Log.e("DEBUG_FLOW", "❌ JSON 파싱 오류", e)
                    binding.emptyText.visibility = android.view.View.VISIBLE
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("DEBUG_FLOW", "❌ 네트워크 실패", t)
                binding.progressContainer.visibility = android.view.View.GONE
                binding.loadingLottie.pauseAnimation()
                binding.emptyText.visibility = android.view.View.VISIBLE
            }
        })
    }

    private fun setupSearchUi(initialQuery: String, initialCategory: Boolean) {
        val searchModeToggle = binding.searchModeToggle
        val searchView = binding.searchBar

        val queryText = if (initialCategory) {
            "\"$initialQuery\" 카테고리"
        } else {
            "\"$initialQuery\" 검색 결과"
        }
        binding.queryText.text = queryText

        searchModeToggle.check(if (initialCategory) R.id.btn_mode_category else R.id.btn_mode_title)
        binding.chipSortLatest.isChecked = true

        searchView.setIconifiedByDefault(false)
        searchView.isIconified = false
        searchView.setQuery(initialQuery, false)
        searchView.clearFocus()

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val keyword = query?.trim().orEmpty()
                if (keyword.isNotEmpty()) {
                    val isCategoryMode = searchModeToggle.checkedButtonId == R.id.btn_mode_category
                    binding.queryText.text = if (isCategoryMode) {
                        "\"$keyword\" 카테고리"
                    } else {
                        "\"$keyword\" 검색 결과"
                    }
                    loadSearchResults(keyword, isCategoryMode)
                }
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean = false
        })

        searchModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val keyword = searchView.query?.toString().orEmpty()
                if (keyword.isNotEmpty()) {
                    val isCategoryMode = checkedId == R.id.btn_mode_category
                    binding.queryText.text = if (isCategoryMode) {
                        "\"$keyword\" 카테고리"
                    } else {
                        "\"$keyword\" 검색 결과"
                    }
                    loadSearchResults(keyword, isCategoryMode)
                }
            }
        }

        binding.chipGroupSort.setOnCheckedChangeListener { _, checkedId ->
            currentSort = when (checkedId) {
                R.id.chip_sort_price_low -> SortOption.PRICE_LOW
                R.id.chip_sort_price_high -> SortOption.PRICE_HIGH
                else -> SortOption.LATEST
            }
            renderProducts()
        }
    }

    private fun renderProducts() {
        if (latestProducts.isEmpty()) {
            binding.emptyText.visibility = android.view.View.VISIBLE
            adapter.updateList(emptyList())
            return
        }

        val sorted = when (currentSort) {
            SortOption.PRICE_LOW -> latestProducts.sortedBy { it.price }
            SortOption.PRICE_HIGH -> latestProducts.sortedByDescending { it.price }
            SortOption.LATEST -> latestProducts
        }

        adapter.updateList(sorted)
        binding.emptyText.visibility = android.view.View.GONE
    }
}
