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
        binding.btnBack.setOnClickListener { finish() }

        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "공유하기 기능 준비중", Toast.LENGTH_SHORT).show()
        }

        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "더보기 기능 준비중", Toast.LENGTH_SHORT).show()
        }

        // 장바구니
        binding.btnCart.setOnClickListener {
            currentProduct?.let { product ->
                CartManager.addItem(product)
                Toast.makeText(this, "장바구니에 담았습니다.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "상품 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
        }

        // 대여하기
        binding.btnRent.setOnClickListener {
            val p = currentProduct ?: return@setOnClickListener

            val intent = Intent(this, RentRequestActivity::class.java).apply {
                putExtra("TITLE", p.title)
                putExtra("PRICE", p.price)
                putExtra("PRICE_UNIT", p.price_unit)
                putExtra("DEPOSIT", p.deposit ?: 0)
                putExtra("ITEM_ID", p.id)
                putExtra("LENDER_ID", p.userId)
                putExtra("SELLER_NICKNAME", p.sellerNickname)
                putExtra("IMAGE_URL", p.imageUrls?.firstOrNull())
            }
            startActivity(intent)
        }

        // 채팅
        binding.btnStartChat.setOnClickListener { startChatting() }
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
        binding.textCategoryTime.text = "${product.category ?: "기타"} · 1분 전"
        binding.textDescription.text = product.description ?: ""

        // 가격 + 단위
        val priceUnitLabel = PriceUnitMapper.toLabel(product.price_unit)
        val priceStr = numberFormat.format(product.price)
        binding.textPrice.text = "$priceStr 원 / $priceUnitLabel"

        val deposit = product.deposit ?: 0
        binding.textDeposit.text =
            if (deposit > 0) "보증금 ${numberFormat.format(deposit)}원"
            else "(보증금 없음)"

        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"
        binding.textSellerAddress.text = product.address ?: "위치 미설정"

        // 이미지
        val fixedImages = product.imageUrls?.mapNotNull { ImageUrlUtils.resolve(it) } ?: emptyList()

        if (fixedImages.isNotEmpty()) {
            binding.viewPagerImages.adapter = DetailImageAdapter(fixedImages)
            binding.textImageIndicator.text = "1 / ${fixedImages.size}"

            binding.viewPagerImages.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.textImageIndicator.text = "${position + 1} / ${fixedImages.size}"
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

        val request = ChatRoomCreateRequest(product.id, product.userId, myId)

        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        val rawData = response.body()?.data
                        val gson = Gson()
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val map = gson.fromJson<Map<String, Any>>(gson.toJson(rawData), type)
                        val roomId = map["roomId"]?.toString()

                        if (roomId != null) {
                            val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                            intent.putExtra("ROOM_ID", roomId)
                            intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                            startActivity(intent)
                        }
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@ProductDetailActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
