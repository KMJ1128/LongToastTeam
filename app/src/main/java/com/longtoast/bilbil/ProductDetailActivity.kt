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
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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
                CartManager.addItem(currentProduct!!)
                Toast.makeText(this, "장바구니에 담았습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "상품 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 대여하기 버튼
        binding.btnRent.setOnClickListener {
            val intent = Intent(this, RentRequestActivity::class.java)
            intent.putExtra("TITLE", binding.textTitle.text.toString())
            intent.putExtra("PRICE", currentProduct?.price ?: 0)
            intent.putExtra("DEPOSIT", currentProduct?.deposit ?: 0)
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
        binding.textTitle.text = product.title
        binding.textCategoryTime.text = formatCategoryAndTime(product.category, product.createdAt)
        binding.textDescription.text = product.description ?: ""

        val priceStr = numberFormat.format(product.price)
        binding.textPrice.text = "$priceStr 원"

        val deposit = product.deposit ?: 0
        if (deposit > 0) {
            binding.textDeposit.text = "보증금 ${numberFormat.format(deposit)}원"
        } else {
            binding.textDeposit.text = "(보증금 없음)"
        }

        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"
        binding.textSellerAddress.text = product.address ?: "위치 미설정"

        val images = product.imageUrls ?: emptyList()
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
                    val roomId = mapData?.get("roomId")?.toString()

                    if (roomId != null) {
                        val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                        intent.putExtra("ROOM_ID", roomId)
                        intent.putExtra("PARTNER_ID", product.userId)
                        intent.putExtra("PARTNER_NICKNAME", product.sellerNickname)
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

    private fun formatCategoryAndTime(category: String?, createdAt: String?): String {
        val categoryLabel = category ?: "기타"
        if (createdAt.isNullOrBlank()) return categoryLabel

        return try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val date = parser.parse(createdAt) ?: return categoryLabel
            val now = System.currentTimeMillis()
            val diffMillis = now - date.time
            val minutes = diffMillis / 60000
            val hours = diffMillis / 3600000
            val days = diffMillis / 86400000

            val relative = when {
                minutes < 1 -> "방금 전"
                minutes < 60 -> "${minutes}분 전"
                hours < 24 -> "${hours}시간 전"
                days < 7 -> "${days}일 전"
                else -> java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()).format(date)
            }

            "$categoryLabel · $relative"
        } catch (e: Exception) {
            Log.e("ProductDetail", "시간 포맷 오류", e)
            categoryLabel
        }
    }
}
