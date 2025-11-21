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
// 필요한 Import 추가
import com.longtoast.bilbil.databinding.FragmentMyItemsBinding
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductDTO

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MyItemsFragment : Fragment() {

    private var _binding: FragmentMyItemsBinding? = null
    // View Binding을 안전하게 접근하기 위한 getter
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Fragment의 뷰 바인딩 초기화
        _binding = FragmentMyItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView 설정
        binding.recyclerViewMyItems.layoutManager = LinearLayoutManager(context)



        val currentUserId = AuthTokenManager.getUserId()
        if (currentUserId != null) {
            Log.e("CURRENT_USER", "✅ 현재 로그인된 사용자 ID: $currentUserId")
        } else {
            Log.e("CURRENT_USER", "❌ 사용자 ID를 찾을 수 없습니다. (로그인 필요)")
        }

        // 내가 등록한 상품 목록 로드
        fetchMyProducts()
    }

    /**
     * 서버에서 내가 등록한 상품 목록을 불러옵니다.
     */
    private fun fetchMyProducts() {
        Log.d("MY_ITEMS", "내가 등록한 상품 목록 조회 API 호출 시작...")

        // 로딩 중이거나 데이터를 가져오는 동안 Empty State 뷰는 잠시 숨김
        binding.recyclerViewMyItems.visibility = View.GONE
        binding.textEmptyState.visibility = View.GONE

        RetrofitClient.getApiService().getMyProducts()
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful || response.body()?.data == null) {
                        Log.e("MY_ITEMS", "조회 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                        Toast.makeText(context, "상품 목록을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()

                        // 🚨 실패 시 Empty State 표시
                        binding.textEmptyState.visibility = View.VISIBLE
                        return
                    }

                    val rawData = response.body()?.data
                    var productList: List<ProductDTO>? = null

                    try {
                        val gson = Gson()
                        // List<ProductListDTO>로 파싱
                        val listType = object : TypeToken<List<ProductDTO>>() {}.type
                        val dataJson = gson.toJson(rawData)
                        productList = gson.fromJson(dataJson, listType)
                    } catch (e: Exception) {
                        Log.e("MY_ITEMS", "List<ProductListDTO> 파싱 중 오류 발생", e)
                    }

                    if (productList != null && productList.isNotEmpty()) {
                        // ✅ [목록 있음] RecyclerView 표시
                        Log.d("MY_ITEMS", "✅ 상품 목록 조회 성공. 개수: ${productList.size}")

                        binding.recyclerViewMyItems.visibility = View.VISIBLE
                        binding.textEmptyState.visibility = View.GONE

                        val adapter = MyItemsAdapter(productList) { product ->
                        // TODO: 상품 클릭 시 상세 화면으로 이동하는 로직 구현
                            Toast.makeText(context, "${product.title} 상세 보기", Toast.LENGTH_SHORT).show()
                        }
                        binding.recyclerViewMyItems.adapter = adapter
                    } else {
                        // ✅ [목록 없음] Empty State 텍스트 표시
                        Log.i("MY_ITEMS", "조회 결과 없음 또는 파싱된 리스트가 비어있음.")
                        Toast.makeText(context, "등록된 상품이 없습니다.", Toast.LENGTH_SHORT).show()

                        binding.recyclerViewMyItems.visibility = View.GONE
                        binding.textEmptyState.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("MY_ITEMS", "서버 통신 오류", t)
                    Toast.makeText(context, "네트워크 오류", Toast.LENGTH_SHORT).show()

                    // 🚨 실패 시 Empty State 표시
                    binding.recyclerViewMyItems.visibility = View.GONE
                    binding.textEmptyState.visibility = View.VISIBLE
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지를 위해 뷰가 파괴될 때 바인딩을 null 처리
        _binding = null
    }
}