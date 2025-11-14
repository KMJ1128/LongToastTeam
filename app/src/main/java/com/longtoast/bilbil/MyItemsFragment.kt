package com.longtoast.bilbil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.FragmentMyItemsBinding
import com.longtoast.bilbil.ui.myitems.MyItemsViewModel
import com.longtoast.bilbil.ui.myitems.MyItemsViewModelFactory
// 프로젝트에 따라 아래 클래스들의 실제 위치에 맞춰 import 하세요.
// import com.longtoast.bilbil.network.ApiClient
// import com.longtoast.bilbil.data.ProductRepository
// import com.longtoast.bilbil.util.TokenManager
// import com.longtoast.bilbil.ui.myitems.ProductAdapter

class MyItemsFragment : Fragment() {

    private var _binding: FragmentMyItemsBinding? = null
    private val binding get() = _binding!!

    // ViewModel 초기화: Factory를 사용하여 Repository 주입
    private val viewModel: MyItemsViewModel by viewModels {
        val repository = ProductRepository(ApiClient.productService)
        MyItemsViewModelFactory(repository)
    }

    private lateinit var productAdapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        // 현재 로그인한 사용자의 ID 가져오기 (TokenManager 구현에 따라 경로/이름 변경)
        val currentUserId = TokenManager.getCurrentUserId()

        if (currentUserId != null) {
            // ViewModel 호출: 사용자 ID를 Controller로 전달
            viewModel.loadMyProducts(currentUserId)
        } else {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_LONG).show()
            // 로그인 화면으로 이동 등 추가 처리 가능
            showEmptyState()
        }
    }

    private fun setupRecyclerView() {
        // 어댑터 초기화: 클릭 리스너 전달
        productAdapter = ProductAdapter(emptyList()) { itemId ->
            // 클릭 이벤트: 상세 화면으로 이동 (구현 필요)
            Toast.makeText(requireContext(), "내 물품 ID: $itemId 상세 화면 이동 준비", Toast.LENGTH_SHORT).show()
            // val intent = Intent(requireContext(), ProductDetailActivity::class.java)
            // intent.putExtra("ITEM_ID", itemId)
            // startActivity(intent)
        }

        binding.myItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = productAdapter
            setHasFixedSize(true)
        }

        // 초기 상태: 로딩 표시(혹은 숨김)
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyTextView.visibility = View.GONE
        binding.myItemsRecyclerView.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewModel.products.observe(viewLifecycleOwner) { list ->
            // Adapter에 데이터 전달: 어댑터 구현에 따라 메서드명을 맞춰주세요.
            try {
                productAdapter.updateList(list)
            } catch (e: NoSuchMethodError) {
                // updateList가 없을 경우 대비(개발 환경에 맞게 수정하세요)
                // 예: (productAdapter as? ListAdapter)?.submitList(list)
            }

            if (list.isEmpty()) {
                showEmptyState()
            } else {
                showContentState()
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            // 로딩 중에는 콘텐츠와 빈 메시지를 숨김
            if (isLoading) {
                binding.myItemsRecyclerView.visibility = View.GONE
                binding.emptyTextView.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrBlank()) {
                Toast.makeText(requireContext(), "내 물품 조회 오류: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.myItemsRecyclerView.visibility = View.GONE
        binding.emptyTextView.visibility = View.VISIBLE
    }

    private fun showContentState() {
        binding.progressBar.visibility = View.GONE
        binding.emptyTextView.visibility = View.GONE
        binding.myItemsRecyclerView.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
