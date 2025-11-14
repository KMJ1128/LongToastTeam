package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.databinding.FragmentHomeBinding

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

        setupCategoryRecyclerView()

        binding.locationText.setOnClickListener {
            val intent = Intent(requireContext(), SettingMapActivity::class.java)
            startActivity(intent)
            Toast.makeText(requireContext(), "지도 설정 화면으로 이동", Toast.LENGTH_SHORT).show()
        }

        binding.menuButton.setOnClickListener {
            val drawerLayout = activity?.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
            drawerLayout?.openDrawer(androidx.core.view.GravityCompat.END)
        }

        // SearchView 처리
        val searchView: SearchView = binding.searchBar
        searchView.queryHint = "검색어를 입력하세요"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim()
                if (q.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return true
                }
                val intent = Intent(requireContext(), SearchResultFragment::class.java).apply {
                    putExtra("SEARCH_QUERY", q)
                    putExtra("SEARCH_IS_CATEGORY", false)
                }
                startActivity(intent)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    private fun setupCategoryRecyclerView() {
        // Category 타입이 명확히 정의되어 있으므로 타입 추론 오류가 발생하지 않습니다.
        val categoryList: List<Category> = listOf(
            Category("자전거", R.drawable.ic_bike, "자전거"),
            Category("캠핑용품", R.drawable.ic_camping, "캠핑용품"),
            Category("디지털", R.drawable.ic_digital, "디지털/가전"),
            Category("도서", R.drawable.ic_book, "도서/티켓"),
            Category("생활가구", R.drawable.ic_furniture, "생활가구"),
            Category("반려동물", R.drawable.ic_pet, "반려동물")
        )

        val layoutManager = GridLayoutManager(requireContext(), 3)
        binding.categoryRecyclerView.layoutManager = layoutManager

        val adapter = CategoryAdapter(categoryList) { category: Category ->
            val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
                putExtra("SEARCH_QUERY", category.searchQuery)
                putExtra("SEARCH_IS_CATEGORY", true)
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "${category.name} 검색", Toast.LENGTH_SHORT).show()
        }

        binding.categoryRecyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** 카테고리 데이터 모델 */
data class Category(
    val name: String,
    val iconResId: Int,
    val searchQuery: String
)

/** 카테고리 어댑터 */
class CategoryAdapter(
    private val categories: List<Category>,
    private val onItemClicked: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.category_icon)
        val name: android.widget.TextView = view.findViewById(R.id.category_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_grid, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.name.text = category.name
        holder.icon.setImageResource(category.iconResId)
        holder.itemView.setOnClickListener {
            onItemClicked(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}