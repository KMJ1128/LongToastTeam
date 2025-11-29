package com.longtoast.bilbil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.FragmentSearchResultBinding

class SearchResultFragment : Fragment() {

    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductAdapter

    private var query: String? = null
    private var isCategory: Boolean = false

    companion object {
        fun newInstance(query: String, isCategory: Boolean): SearchResultFragment {
            val fragment = SearchResultFragment()
            val args = Bundle()
            args.putString("SEARCH_QUERY", query)
            args.putBoolean("SEARCH_IS_CATEGORY", isCategory)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        query = arguments?.getString("SEARCH_QUERY")
        isCategory = arguments?.getBoolean("SEARCH_IS_CATEGORY") ?: false

        adapter = ProductAdapter(emptyList()) { itemId ->
            Toast.makeText(requireContext(), "아이템 ID: $itemId 클릭", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.queryText.text = if (isCategory) "\"$query\" 카테고리" else "\"$query\" 검색 결과"

        // ViewModel 호출 등 기존 로직 유지 (binding.recyclerView, binding.progressBar, binding.emptyText)
        // 이 Fragment는 Activity와 독립적으로 검색 결과를 표시하는 역할만 수행합니다.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}