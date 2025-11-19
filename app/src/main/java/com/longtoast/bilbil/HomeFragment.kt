package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import kotlin.jvm.java

// Category, CategoryAdapter 클래스가 필요합니다! (기존 HomeActivity.kt 파일 맨 아래에 있던 클래스들을 여기에 복사해주세요)

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Fragment의 뷰 바인딩 초기화
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 카테고리 RecyclerView 설정 함수 호출
        setupCategoryRecyclerView()

        // 2. 지역 설정 텍스트 리스너 (Fragment에서 Activity 실행)
        binding.locationText.setOnClickListener {
// 🚨 [핵심 수정] AuthTokenManager를 통해 USER_ID와 SERVICE_TOKEN을 가져옵니다.
            val currentUserId = AuthTokenManager.getUserId()
            val serviceToken = AuthTokenManager.getToken()

            // 🚨 [수정된 핵심 로직] USER_ID가 null이거나 -1(기본값)인 경우 진입 차단
            if (currentUserId == null || currentUserId == -1 || serviceToken == null) {
                // 토큰이나 ID가 없으면 오류 메시지를 띄우고 로그아웃 화면(MainActivity)으로 유도하는 것이 안전합니다.
                Toast.makeText(requireContext(), "로그인 정보가 유효하지 않습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                // 필요하다면: startActivity(Intent(requireContext(), MainActivity::class.java))
                return@setOnClickListener
            }

            val intent = Intent(requireContext(), SettingMapActivity::class.java).apply {
                // 🚨 Intent에 USER_ID와 SERVICE_TOKEN을 담아 전달합니다.
                putExtra("USER_ID", currentUserId)
                putExtra("SERVICE_TOKEN", serviceToken)

                // 🔑 [수정된 부분] HomeFragment에서 이동하는 것은 '업데이트'이므로 SETUP_MODE를 false로 명시합니다.
                putExtra("SETUP_MODE", false)
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "지도 설정 화면으로 이동", Toast.LENGTH_SHORT).show()
        }

        // 여기에 검색 바, 메뉴 버튼 등 Home 화면의 모든 리스너를 넣습니다.
        binding.menuButton.setOnClickListener {
            // Activity에서 DrawerLayout을 찾아서 엽니다.
            val drawerLayout = activity?.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
            drawerLayout?.openDrawer(androidx.core.view.GravityCompat.END) // 오른쪽에서 열기
        }
    }

    private fun setupCategoryRecyclerView() {
        // 1. 카테고리 데이터 준비 (Category 클래스와 Adapter가 필요합니다!)
        val categoryList = listOf(
            Category("자전거", R.drawable.ic_bike, "자전거"),
            Category("캠핑용품", R.drawable.ic_camping, "캠핑용품"),
            Category("디지털", R.drawable.ic_digital, "디지털/가전"),
            Category("도서", R.drawable.ic_book, "도서/티켓"),
            Category("생활가구", R.drawable.ic_furniture, "생활가구"),
            Category("반려동물", R.drawable.ic_pet, "반려동물")
        )

        // 2. GridLayoutManager 설정 (this 대신 requireContext() 사용)
        val layoutManager = GridLayoutManager(requireContext(), 3)
        binding.categoryRecyclerView.layoutManager = layoutManager

        // 3. 어댑터 설정 및 클릭 이벤트 정의
        val adapter = CategoryAdapter(categoryList) { category ->
            // Fragment에서 Activity 실행
            val intent = Intent(requireContext(), SearchResultActivity::class.java).apply {
                putExtra("SEARCH_QUERY", category.searchQuery)
            }
            startActivity(intent)
            Toast.makeText(requireContext(), "${category.name} 검색", Toast.LENGTH_SHORT).show()
        }

        binding.categoryRecyclerView.adapter = adapter
    }

    // Fragment가 파괴될 때 바인딩을 null로 설정하여 메모리 누수를 방지합니다.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
// 카테고리 데이터 모델
data class Category(
    val name: String,
    val iconResId: Int,
    val searchQuery: String
)

// 카테고리 RecyclerView 어댑터
class CategoryAdapter(
    private val categories: List<Category>,
    private val onItemClicked: (Category) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        // R.id.category_icon, R.id.category_name이 fragment_home.xml에 정의되어 있어야 합니다.
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