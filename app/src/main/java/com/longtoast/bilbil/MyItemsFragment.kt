package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
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
        b.emptyAnimation.visibility = View.GONE
        b.textEmptyState.visibility = View.GONE

        b.recyclerViewMyItems.visibility = View.VISIBLE
        b.recyclerViewMyItems.adapter = MyItemsAdapter(list) { product ->
            startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                .putExtra("ITEM_ID", product.id))
        }
        val adapter = MyItemsAdapter(
            productList = list,
            onItemClicked = { product ->
                // 아이템 클릭 시 상세 페이지로 이동
                val intent = Intent(requireContext(), ProductDetailActivity::class.java).apply {
                    putExtra("ITEM_ID", product.id)
                }
                startActivity(intent)
            },
            onReviewClicked = { product ->
                // ✅ "렌트한 물품" 탭에서만 의미 있음
                if (currentTab != Tab.RENTED) {
                    // 혹시나 등록 탭에서 들어오면 막아두기
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
            }
        )

        binding.recyclerViewMyItems.adapter = adapter
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
