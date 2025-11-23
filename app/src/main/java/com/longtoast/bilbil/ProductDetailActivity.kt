// com.longtoast.bilbil.ProductDetailActivity.kt
package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.longtoast.bilbil.adapter.DetailImageAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityProductDetailBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductDTO
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: ProductDTO? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 인텐트로 넘어온 itemId 받기
        val itemId = intent.getIntExtra("ITEM_ID", -1)

        if (itemId == -1) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadProductDetail(itemId)
    }

    private fun setupUI() {
        // 뒤로가기 버튼
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 채팅하기 버튼
        binding.btnStartChat.setOnClickListener {
            startChatting()
        }
    }

    // 서버에서 상세 정보 가져오기
    private fun loadProductDetail(itemId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().getProductDetail(itemId)

                if (response.isSuccessful && response.body() != null) {
                    val rawData = response.body()!!.data

                    // Gson을 이용해 JSON -> ProductDTO 파싱
                    val gson = Gson()
                    val json = gson.toJson(rawData)
                    val product = gson.fromJson(json, ProductDTO::class.java)

                    currentProduct = product
                    updateUI(product)
                } else {
                    Log.e("API_FAIL", "Response Code: ${response.code()}")
                    Toast.makeText(this@ProductDetailActivity, "상품 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProductDetail", "상세 정보 로드 실패", e)
                Toast.makeText(this@ProductDetailActivity, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 가져온 정보로 화면 업데이트
    private fun updateUI(product: ProductDTO) {
        // 1. 텍스트 정보 바인딩
        binding.textTitle.text = product.title
        binding.textCategory.text = product.category ?: "카테고리 없음"
        binding.textDescription.text = product.description ?: "내용 없음"

        // 주소 표시 (address가 없으면 tradeLocation 사용)
        binding.textAddress.text = product.address ?: product.tradeLocation ?: "위치 정보 없음"

        // 가격 표시 (단위는 설명이나 별도 필드에서 파싱해야 하나, 일단 '일'로 고정하거나 DTO에 추가 필요)
        binding.textPrice.text = "₩ ${String.format("%,d", product.price)} / 일"

        // 보증금
        val deposit = product.deposit ?: 0
        binding.textDeposit.text = if (deposit > 0) "보증금 ₩ ${String.format("%,d", deposit)}" else "보증금 없음"

        // 🔥 [수정됨] 판매자 닉네임 표시 (백엔드 DTO에 sellerNickname이 있으므로 사용)
        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"

        // 2. 이미지 슬라이더 (Base64 리스트)
        val images = product.imageUrls ?: emptyList()
        if (images.isNotEmpty()) {
            val adapter = DetailImageAdapter(images)
            binding.viewPagerImages.adapter = adapter
            binding.textImageIndicator.text = "1 / ${images.size}"

            // 페이지 넘길 때 숫자 변경
            binding.viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.textImageIndicator.text = "${position + 1} / ${images.size}"
                }
            })
        } else {
            binding.viewPagerImages.visibility = View.GONE
            binding.textImageIndicator.visibility = View.GONE
        }

        // 3. 내 물건이면 '채팅하기' 버튼 숨기기
        val myId = AuthTokenManager.getUserId()
        // DTO에서 userId는 @SerializedName("sellerId")로 매핑되어 있음
        if (myId != null && myId == product.userId) {
            binding.btnStartChat.visibility = View.GONE
        }
    }

    // 채팅방 생성 요청
    private fun startChatting() {
        val myId = AuthTokenManager.getUserId()
        val product = currentProduct ?: return

        if (myId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ChatRoomCreateRequest(
            itemId = product.id,
            lenderId = product.userId, // 판매자 ID
            borrowerId = myId          // 구매자(나) ID
        )

        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        val rawData = response.body()?.data
                        val gson = Gson()
                        // Map 형태로 파싱하여 roomId 추출
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val mapData: Map<String, Any>? = gson.fromJson(gson.toJson(rawData), type)

                        // roomId가 숫자일 수도, 문자일 수도 있으므로 안전하게 변환
                        val roomId = mapData?.get("roomId")?.toString()

                        if (roomId != null) {
                            val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                            intent.putExtra("ROOM_ID", roomId)
                            intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                            startActivity(intent)
                        } else {
                            // 이미 존재하는 방일 경우 서버 메시지나 로직에 따라 처리
                            Toast.makeText(this@ProductDetailActivity, "채팅방 입장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ProductDetailActivity, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@ProductDetailActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}