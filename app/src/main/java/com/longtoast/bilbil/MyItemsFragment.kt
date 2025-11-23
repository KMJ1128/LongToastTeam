// java/com/longtoast/bilbil/MyItemsFragment.kt
package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

        // RecyclerView 기본 설정
        binding.recyclerViewMyItems.layoutManager = LinearLayoutManager(context)

        // 토글 기본 선택: 등록한 물품
        binding.toggleMyActivity.check(binding.btnRegistered.id)
        setupToggle()

        // 처음 들어왔을 땐 "등록한 물품" 탭 데이터 로드
        loadRegisteredItems()
    }

    // 상단 토글 클릭 시 동작
    private fun setupToggle() {
        binding.toggleMyActivity.addOnButtonCheckedListener { group: MaterialButtonToggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                binding.btnRegistered.id -> {
                    currentTab = Tab.REGISTERED
                    binding.textEmptyState.text = "등록한 상품이 없습니다."
                    if (registeredItems.isEmpty()) {
                        loadRegisteredItems()
                    } else {
                        showList(registeredItems)
                    }
                }

                binding.btnRented.id -> {
                    currentTab = Tab.RENTED
                    binding.textEmptyState.text = "렌트한 상품이 없습니다."
                    if (rentedItems.isEmpty()) {
                        loadRentedItems()
                    } else {
                        showList(rentedItems)
                    }
                }
            }
        }
    }

    // (1) 내가 등록한 물품 불러오기
    private fun loadRegisteredItems() {
        Log.d("MY_ACTIVITY", "내가 등록한 물품 목록 요청")

        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE

        RetrofitClient.getApiService()
            .getMyRegisteredProducts()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!isAdded || _binding == null) return

                    if (!response.isSuccessful) {
                        Log.e("MY_ACTIVITY", "❌ 등록한 물품 API 실패: code=${response.code()}")
                        showEmptyState("등록한 상품이 없습니다.")
                        return
                    }

                    val rawData = response.body()?.data
                    Log.d("MY_ACTIVITY", "등록한 물품 rawData = $rawData")

                    if (rawData == null) {
                        showEmptyState("등록한 상품이 없습니다.")
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<ProductDTO>>() {}.type
                        val json = gson.toJson(rawData)
                        val list: List<ProductDTO> = gson.fromJson(json, listType)

                        registeredItems = list

                        if (list.isEmpty()) {
                            showEmptyState("등록한 상품이 없습니다.")
                        } else {
                            // 현재 탭이 등록 탭일 때만 보여줌
                            if (currentTab == Tab.REGISTERED) {
                                showList(list)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MY_ACTIVITY", "❌ JSON 파싱 오류(등록한 물품)", e)
                        showEmptyState("등록한 상품이 없습니다.")
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    Log.e("MY_ACTIVITY", "❌ 네트워크 오류(등록한 물품)", t)
                    Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
                    showEmptyState("등록한 상품이 없습니다.")
                }
            })
    }

    // (2) 내가 렌트한 물품 불러오기
    private fun loadRentedItems() {
        Log.d("MY_ACTIVITY", "내가 렌트한 물품 목록 요청")

        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE

        RetrofitClient.getApiService()
            .getMyRentedProducts()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!isAdded || _binding == null) return

                    if (!response.isSuccessful) {
                        Log.e("MY_ACTIVITY", "❌ 렌트한 물품 API 실패: code=${response.code()}")
                        showEmptyState("렌트한 상품이 없습니다.")
                        return
                    }

                    val rawData = response.body()?.data
                    Log.d("MY_ACTIVITY", "렌트한 물품 rawData = $rawData")

                    if (rawData == null) {
                        showEmptyState("렌트한 상품이 없습니다.")
                        return
                    }

                    try {
                        val gson = Gson()
                        val listType = object : TypeToken<List<ProductDTO>>() {}.type
                        val json = gson.toJson(rawData)
                        val list: List<ProductDTO> = gson.fromJson(json, listType)

                        rentedItems = list

                        if (list.isEmpty()) {
                            showEmptyState("렌트한 상품이 없습니다.")
                        } else {
                            if (currentTab == Tab.RENTED) {
                                showList(list)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MY_ACTIVITY", "❌ JSON 파싱 오류(렌트한 물품)", e)
                        showEmptyState("렌트한 상품이 없습니다.")
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    if (!isAdded || _binding == null) return
                    Log.e("MY_ACTIVITY", "❌ 네트워크 오류(렌트한 물품)", t)
                    Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()
                    showEmptyState("렌트한 상품이 없습니다.")
                }
            })
    }

    // 실제 리스트를 RecyclerView에 뿌려주는 부분
    private fun showList(list: List<ProductDTO>) {
        binding.textEmptyState.visibility = View.GONE
        binding.recyclerViewMyItems.visibility = View.VISIBLE

        val adapter = MyItemsAdapter(list) { product ->
            // 아이템 클릭 시 상세 페이지로 이동
            val intent = Intent(requireContext(), ProductDetailActivity::class.java).apply {
                putExtra("ITEM_ID", product.id)
            }
            startActivity(intent)
        }
        binding.recyclerViewMyItems.adapter = adapter
    }

    // 비어있을 때 문구 표시
    private fun showEmptyState(message: String) {
        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.VISIBLE
        binding.textEmptyState.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
