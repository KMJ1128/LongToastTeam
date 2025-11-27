package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.FragmentMyItemsBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductDTO
import retrofit2.*

class MyItemsFragment : Fragment() {

    private var _binding: FragmentMyItemsBinding? = null
    private val binding get() = _binding!!

    private var registeredItems: List<ProductDTO> = emptyList()
    private var rentedItems: List<ProductDTO> = emptyList()

    private enum class Tab { REGISTERED, RENTED }
    private var currentTab: Tab = Tab.REGISTERED

    // -----------------------------------------------------
    // 🔥 binding null-safe wrapper (모든 UI 변경은 이 안에서만!)
    // -----------------------------------------------------
    private fun safe(action: (FragmentMyItemsBinding) -> Unit) {
        if (!isAdded || _binding == null) return
        action(binding)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        safe { b ->
            b.recyclerViewMyItems.layoutManager = LinearLayoutManager(context)
            b.toggleMyActivity.check(b.btnRegistered.id)

            b.swipeRefresh.setOnRefreshListener {
                if (currentTab == Tab.REGISTERED) {
                    loadRegisteredItems()
                } else {
                    loadRentedItems()
                }
            }
        }

        setupToggle()
        loadRegisteredItems()
    }

    // -----------------------------------------------------
    // 🔥 로딩 애니메이션
    // -----------------------------------------------------
    private fun showLoading() = safe { b ->
        b.loadingAnimation.visibility = View.VISIBLE
        b.loadingAnimation.repeatCount = -1
        b.loadingAnimation.playAnimation()

        b.recyclerViewMyItems.visibility = View.GONE
        b.textEmptyState.visibility = View.GONE
        b.emptyAnimation.visibility = View.GONE
    }

    private fun hideLoading() = safe { b ->
        b.loadingAnimation.cancelAnimation()
        b.loadingAnimation.visibility = View.GONE
        b.swipeRefresh.isRefreshing = false
    }

    // -----------------------------------------------------
    // 🔥 탭 전환
    // -----------------------------------------------------
    private fun setupToggle() = safe { b ->
        b.toggleMyActivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            resetUI()

            when (checkedId) {
                b.btnRegistered.id -> {
                    currentTab = Tab.REGISTERED
                    b.textEmptyState.text = "등록한 상품이 없습니다."
                    if (registeredItems.isEmpty()) loadRegisteredItems()
                    else showList(registeredItems)
                }

                b.btnRented.id -> {
                    currentTab = Tab.RENTED
                    b.textEmptyState.text = "렌트한 상품이 없습니다."
                    if (rentedItems.isEmpty()) loadRentedItems()
                    else showList(rentedItems)
                }
            }
        }
    }

    private fun resetUI() = safe { b ->
        b.recyclerViewMyItems.visibility = View.GONE
        b.textEmptyState.visibility = View.GONE
        b.emptyAnimation.visibility = View.GONE
        b.loadingAnimation.visibility = View.GONE
        b.loadingAnimation.cancelAnimation()
    }

    // -----------------------------------------------------
    // 🔥 등록한 물품
    // -----------------------------------------------------
    private fun loadRegisteredItems() {
        showLoading()

        RetrofitClient.getApiService()
            .getMyRegisteredProducts()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    hideLoading()
                    if (!isAdded || _binding == null) return

                    val raw = response.body()?.data
                    if (!response.isSuccessful || raw == null) {
                        showEmptyState("등록한 상품이 없습니다.")
                        return
                    }

                    val listType = object : TypeToken<List<ProductDTO>>() {}.type
                    registeredItems = Gson().fromJson(Gson().toJson(raw), listType)

                    if (registeredItems.isEmpty()) showEmptyState("등록한 상품이 없습니다.")
                    else if (currentTab == Tab.REGISTERED) showList(registeredItems)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    hideLoading()
                    showEmptyState("등록한 상품이 없습니다.")
                }
            })
    }

    // -----------------------------------------------------
    // 🔥 렌트한 물품
    // -----------------------------------------------------
    private fun loadRentedItems() {
        showLoading()

        RetrofitClient.getApiService()
            .getMyRentedProducts()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    hideLoading()
                    if (!isAdded || _binding == null) return

                    val raw = response.body()?.data
                    if (!response.isSuccessful || raw == null) {
                        showEmptyState("렌트한 상품이 없습니다.")
                        return
                    }

                    val listType = object : TypeToken<List<ProductDTO>>() {}.type
                    rentedItems = Gson().fromJson(Gson().toJson(raw), listType)

                    if (rentedItems.isEmpty()) showEmptyState("렌트한 상품이 없습니다.")
                    else if (currentTab == Tab.RENTED) showList(rentedItems)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    hideLoading()
                    showEmptyState("렌트한 상품이 없습니다.")
                }
            })
    }

    // -----------------------------------------------------
    // 🔥 리스트 표시
    // -----------------------------------------------------
    private fun showList(list: List<ProductDTO>) = safe { b ->
        // 로딩/empty 상태는 숨기고
        b.loadingAnimation.cancelAnimation()
        b.loadingAnimation.visibility = View.GONE
        b.emptyAnimation.visibility = View.GONE
        b.textEmptyState.visibility = View.GONE

        // ✅ 리스트는 보여주기
        b.recyclerViewMyItems.visibility = View.VISIBLE

        val adapter = MyItemsAdapter(
            productList = list,
            onItemClicked = { product ->
                val intent = Intent(requireContext(), ProductDetailActivity::class.java).apply {
                    putExtra("ITEM_ID", product.id)
                }
                startActivity(intent)
            },
            onReviewClicked = { product ->
                if (currentTab != Tab.RENTED) {
                    Toast.makeText(requireContext(), "렌트한 물품에서만 리뷰를 작성할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    return@MyItemsAdapter
                }

                val transactionId = product.transactionId
                if (transactionId == null) {
                    Toast.makeText(requireContext(), "거래 정보가 없어 리뷰를 작성할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@MyItemsAdapter
                }

                val intent = Intent(requireContext(), ReviewActivity::class.java).apply {
                    putExtra("TRANSACTION_ID", transactionId.toInt())
                }
                startActivity(intent)
            },
            onEditClicked = { product -> openEditScreen(product) },
            onDeleteClicked = { product -> confirmDelete(product) }
        )

        b.recyclerViewMyItems.adapter = adapter
    }

    private fun openEditScreen(product: ProductDTO) {
        val host = activity as? HomeHostActivity
        host?.setBottomNavVisibility(View.GONE)

        parentFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, NewPostFragment.newInstance(product))
            .addToBackStack(null)
            .commit()
    }

    private fun confirmDelete(product: ProductDTO) {
        AlertDialog.Builder(requireContext())
            .setTitle("상품 삭제")
            .setMessage("'${product.title}' 상품을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deleteProduct(product) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteProduct(product: ProductDTO) {
        RetrofitClient.getApiService().deleteProduct(product.id)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        if (currentTab == Tab.REGISTERED) loadRegisteredItems() else loadRentedItems()
                    } else {
                        Toast.makeText(requireContext(), "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(requireContext(), "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // -----------------------------------------------------
    // 🔥 Empty 상태
    // -----------------------------------------------------
    private fun showEmptyState(message: String) = safe { b ->
        b.recyclerViewMyItems.visibility = View.GONE
        b.textEmptyState.text = message
        b.textEmptyState.visibility = View.VISIBLE

        b.emptyAnimation.visibility = View.VISIBLE
        b.emptyAnimation.repeatCount = 0
        b.emptyAnimation.playAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
