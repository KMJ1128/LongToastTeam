package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.ActivitySearchResultBinding

class SearchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchResultBinding
    private lateinit var adapter: ProductAdapter

    // ViewModel 생성: repository에 ApiClient.productService 주입
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(ProductRepository(ApiClient.productService))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 연결 및 뒤로가기 아이콘 설정
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (binding.toolbar.navigationIcon == null) {
            binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        // RecyclerView + Adapter
        adapter = ProductAdapter(emptyList()) { itemId ->
            // 💡 아이템 클릭 시 실행될 로직: 물품 상세 화면으로 이동

            // (예시) ProductDetailActivity로 ID를 담아 화면 이동
            val detailIntent = Intent(this, ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", itemId)
            }
            startActivity(detailIntent)

            // Toast.makeText(this, "아이템 ID: $itemId 상세 보기", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        // Intent로부터 전달된 검색 정보
        val query = intent.getStringExtra("SEARCH_QUERY") ?: ""
        val isCategory = intent.getBooleanExtra("SEARCH_IS_CATEGORY", false)

        binding.queryText.text = if (isCategory) "\"$query\" 카테고리" else "\"$query\" 검색 결과"

        // Observe ViewModel
        viewModel.products.observe(this) { list ->
            adapter.updateList(list)
            binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        // 실제 호출: 카테고리인지(파라미터 category)인지(파라미터 title)인지 구분
        if (query.isBlank()) {
            // 쿼리 없으면 빈 화면
            binding.emptyText.visibility = View.VISIBLE
        } else {
            if (isCategory) {
                // category 파라미터에 값 전달, title은 null
                viewModel.loadProducts(searchQuery = null, category = query)
            } else {
                // title 파라미터에 값 전달
                viewModel.loadProducts(searchQuery = query, category = null)
            }
        }

        // 하단의 명확한 뒤로가기 버튼(선택적)
        binding.backButton.setOnClickListener { finish() }
    }
}
