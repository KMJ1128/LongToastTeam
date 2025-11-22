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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.longtoast.bilbil.adapter.CategoryAdapter
import com.longtoast.bilbil.databinding.FragmentHomeBinding
import android.widget.EditText
import androidx.appcompat.widget.SearchView

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

        // -----------------------------------------------------------------------------------------
        // 🔥 SearchView 내부 EditText 가져오기
        // -----------------------------------------------------------------------------------------
        val searchEditTextId = binding.searchBar.context.resources
            .getIdentifier("search_src_text", "id", binding.searchBar.context.packageName)

        val searchEditText = binding.searchBar.findViewById<EditText>(searchEditTextId)

        // 🔥 IME 옵션 강제 설정
        searchEditText.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchEditText.setSingleLine(true)

        // -----------------------------------------------------------------------------------------
        // ⭐⭐ 방법 1 적용: SearchView 클릭 시 자동으로 확장 + 포커스 + 키보드 표시
        // -----------------------------------------------------------------------------------------
        binding.searchBar.setOnClickListener {
            binding.searchBar.isIconified = false      // SearchView 강제 펼치기
            binding.searchBar.requestFocus()           // 포커스 주기
            searchEditText.requestFocus()

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }

        // -----------------------------------------------------------------------------------------
        // 🔥 Enter 입력 시 검색 수행
        // -----------------------------------------------------------------------------------------
        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {

                val query = binding.searchBar.query.toString()
                Log.d("DEBUG_FLOW", "Enter 감지! 검색 실행 → $query")

                if (query.isNotEmpty()) {
                    val intent = Intent(requireContext(), SearchResultActivity::class.java)
                    intent.putExtra("SEARCH_QUERY", query)
                    intent.putExtra("SEARCH_IS_CATEGORY", false)

                    Log.d("DEBUG_FLOW", "SearchResultActivity 이동 → query=$query")

                    startActivity(intent)
                    binding.searchBar.clearFocus()
                }
                true
            } else false
        }

        binding.searchBar.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // -----------------------------------------------------------------------------------------
        // 🔥 카테고리 RecyclerView 설정
        // -----------------------------------------------------------------------------------------
        setupCategoryRecycler()
    }

    private fun setupCategoryRecycler() {
        val categoryList = listOf("자전거", "가구", "캠핑", "전자제품", "운동", "의류")

        Log.d("DEBUG_FLOW", "카테고리 리스트 로드 완료: $categoryList")

        binding.categoryRecyclerView.layoutManager =
            GridLayoutManager(requireContext(), 3)

        binding.categoryRecyclerView.adapter =
            CategoryAdapter(categoryList) { categoryName ->

                Log.d("DEBUG_FLOW", "카테고리 클릭됨 → $categoryName")

                val intent = Intent(requireContext(), SearchResultActivity::class.java)
                intent.putExtra("SEARCH_QUERY", categoryName)
                intent.putExtra("SEARCH_IS_CATEGORY", true)

                startActivity(intent)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
