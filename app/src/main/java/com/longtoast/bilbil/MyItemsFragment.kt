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

        binding.recyclerViewMyItems.layoutManager = LinearLayoutManager(context)

        binding.toggleMyActivity.check(binding.btnRegistered.id)
        setupToggle()

        loadRegisteredItems()
    }

    // ----------------------------
    // 로딩 애니메이션 제어
    // ----------------------------
    private fun showLoading() {
        binding.loadingAnimation.visibility = View.VISIBLE
        binding.loadingAnimation.playAnimation()

        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE
        binding.emptyAnimation.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.loadingAnimation.cancelAnimation()
        binding.loadingAnimation.visibility = View.GONE
    }

    // ----------------------------
    // 탭 전환
    // ----------------------------
    private fun setupToggle() {
        binding.toggleMyActivity.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            resetUI()

            when (checkedId) {
                binding.btnRegistered.id -> {
                    currentTab = Tab.REGISTERED
                    binding.textEmptyState.text = "등록한 상품이 없습니다."

                    if (registeredItems.isEmpty()) loadRegisteredItems()
                    else showList(registeredItems)
                }

                binding.btnRented.id -> {
                    currentTab = Tab.RENTED
                    binding.textEmptyState.text = "렌트한 상품이 없습니다."

                    if (rentedItems.isEmpty()) loadRentedItems()
                    else showList(rentedItems)
                }
            }
        }
    }

    private fun resetUI() {
        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE
        binding.emptyAnimation.visibility = View.GONE
        binding.loadingAnimation.visibility = View.GONE
        binding.loadingAnimation.cancelAnimation()
    }

    // ----------------------------
    // 등록한 물품
    // ----------------------------
    private fun loadRegisteredItems() {
        showLoading()

        RetrofitClient.getApiService()
            .getMyRegisteredProducts()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    hideLoading()
                    if (!isAdded || _binding == null) return

                    if (!response.isSuccessful || response.body()?.data == null) {
                        showEmptyState("등록한 상품이 없습니다.")
                        return
                    }

                    val gson = Gson()
                    val type = object : TypeToken<List<ProductDTO>>() {}.type
                    val list: List<ProductDTO> =
                        gson.fromJson(gson.toJson(response.body()!!.data), type)

                    registeredItems = list

                    if (list.isEmpty()) showEmptyState("등록한 상품이 없습니다.")
                    else if (currentTab == Tab.REGISTERED) showList(list)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    hideLoading()
                    showEmptyState("등록한 상품이 없습니다.")
                }
            })
    }

    // ----------------------------
    // 렌트한 물품
    // ----------------------------
    private fun loadRentedItems() {
        showLoading()

        RetrofitClient.getApiService()
            .getMyRentedProducts()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    hideLoading()
                    if (!isAdded || _binding == null) return

                    if (!response.isSuccessful || response.body()?.data == null) {
                        showEmptyState("렌트한 상품이 없습니다.")
                        return
                    }

                    val gson = Gson()
                    val type = object : TypeToken<List<ProductDTO>>() {}.type
                    val list: List<ProductDTO> =
                        gson.fromJson(gson.toJson(response.body()!!.data), type)

                    rentedItems = list

                    if (list.isEmpty()) showEmptyState("렌트한 상품이 없습니다.")
                    else if (currentTab == Tab.RENTED) showList(list)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    hideLoading()
                    showEmptyState("렌트한 상품이 없습니다.")
                }
            })
    }

    // ----------------------------
    // 리스트 표시
    // ----------------------------
    private fun showList(list: List<ProductDTO>) {
        binding.emptyAnimation.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE
        binding.recyclerViewMyItems.visibility = View.VISIBLE

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

    // ----------------------------
    // Empty 상태
    // ----------------------------
    private fun showEmptyState(message: String) {
        binding.recyclerViewMyItems.visibility = View.GONE

        binding.textEmptyState.text = message
        binding.textEmptyState.visibility = View.VISIBLE

        binding.emptyAnimation.visibility = View.VISIBLE
        binding.emptyAnimation.repeatCount = 0
        binding.emptyAnimation.playAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
