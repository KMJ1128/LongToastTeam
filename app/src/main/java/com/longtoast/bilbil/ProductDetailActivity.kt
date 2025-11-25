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
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.adapter.DetailImageAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityProductDetailBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductDTO
import com.longtoast.bilbil.util.ImageUrlUtils
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: ProductDTO? = null
    private val numberFormat = DecimalFormat("#,###")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val itemId = intent.getIntExtra("ITEM_ID", -1)
        if (itemId == -1) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        loadProductDetail(itemId)
    }

    private fun setupListeners() {
        // 뒤로가기
        binding.btnBack.setOnClickListener { finish() }

        // 공유, 더보기 (기능 준비중)
        binding.btnShare.setOnClickListener { Toast.makeText(this, "공유하기", Toast.LENGTH_SHORT).show() }
        binding.btnMore.setOnClickListener { Toast.makeText(this, "더보기", Toast.LENGTH_SHORT).show() }

        // 1. 채팅하기 버튼
        binding.btnStartChat.setOnClickListener { startChatting() }

// 2. 장바구니 버튼 클릭 이벤트 수정
        binding.btnCart.setOnClickListener {
            if (currentProduct != null) {
                // 1. 매니저에 상품 추가
                CartManager.addItem(currentProduct!!)

                // 2. 사용자에게 알림
                Toast.makeText(this, "장바구니에 담았습니다.", Toast.LENGTH_SHORT).show()

                // (선택사항) 바로 장바구니로 이동하고 싶다면 아래 주석 해제
                // val intent = Intent(this, CartActivity::class.java)
                // startActivity(intent)
            } else {
                Toast.makeText(this, "상품 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
            }
        }

// 3. 대여하기 버튼
        binding.btnRent.setOnClickListener {
            val intent = Intent(this, RentRequestActivity::class.java)

            // 상세 정보를 넘겨줍니다 (옵션)
            intent.putExtra("TITLE", binding.textTitle.text.toString())
            intent.putExtra("PRICE", currentProduct?.price ?: 0)
            intent.putExtra("DEPOSIT", currentProduct?.deposit ?: 0)
            // intent.putExtra("IMAGE_URL", currentProduct?.imageUrls?.firstOrNull())

            startActivity(intent)
        }
    }

    private fun loadProductDetail(itemId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().getProductDetail(itemId)
                if (response.isSuccessful && response.body() != null) {
                    val rawData = response.body()!!.data
                    val gson = Gson()
                    val product = gson.fromJson(gson.toJson(rawData), ProductDTO::class.java)
                    currentProduct = product
                    updateUI(product)
                } else {
                    Toast.makeText(this@ProductDetailActivity, "정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProductDetail", "Load Error", e)
            }
        }
    }

    private fun updateUI(product: ProductDTO) {
        // 1. 텍스트 정보 바인딩
        binding.textTitle.text = product.title
        binding.textCategoryTime.text = "${product.category ?: "기타"} · 1분 전"
        binding.textDescription.text = product.description ?: ""

        // 가격 및 보증금 (본문에 표시)
        val priceStr = numberFormat.format(product.price)
        binding.textPrice.text = "$priceStr 원" // 단위(일/월)는 필요시 추가

        val deposit = product.deposit ?: 0
        if (deposit > 0) {
            binding.textDeposit.text = "보증금 ${numberFormat.format(deposit)}원"
        } else {
            binding.textDeposit.text = "(보증금 없음)"
        }

        // 판매자 정보
        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"
        binding.textSellerAddress.text = product.address ?: "위치 미설정"

        // 2. 이미지 슬라이더
        val images = ImageUrlUtils.buildFullUrls(product.imageUrls)
        if (images.isNotEmpty()) {
            binding.viewPagerImages.adapter = DetailImageAdapter(images)
            binding.textImageIndicator.text = "1 / ${images.size}"
            binding.viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.textImageIndicator.text = "${position + 1} / ${images.size}"
                }
            })
        } else {
            binding.textImageIndicator.visibility = View.GONE
        }

        // 3. 내 물건인 경우 채팅 버튼 숨김 로직 (필요시 주석 해제)
        /*
        val myId = AuthTokenManager.getUserId()
        if (myId != null && myId == product.userId) {
            binding.btnStartChat.visibility = View.GONE
        }
        */
    }

    private fun startChatting() {
        val myId = AuthTokenManager.getUserId()
        val product = currentProduct ?: return

        if (myId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ChatRoomCreateRequest(
            itemId = product.id,
            lenderId = product.userId,
            borrowerId = myId
        )

        RetrofitClient.getApiService().createChatRoom(request).enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                if (response.isSuccessful) {
                    val rawData = response.body()?.data
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val mapData: Map<String, Any>? = gson.fromJson(gson.toJson(rawData), type)
                    val roomId = mapData?.get("roomId")?.toString()?.toIntOrNull()

                    if (roomId != null) {
                        val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                        intent.putExtra("ROOM_ID", roomId)
                        intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                        startActivity(intent)
                    } else {
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