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
import java.text.SimpleDateFormat

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: ProductDTO? = null
    private val numberFormat = DecimalFormat("#,###")
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

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
            val intent = Intent(this, RentRequestActivity::class.java)

            // 상세 정보를 넘겨줍니다 (옵션)
            intent.putExtra("TITLE", binding.textTitle.text.toString())
            intent.putExtra("PRICE", currentProduct?.price ?: 0)
            intent.putExtra("DEPOSIT", currentProduct?.deposit ?: 0)
            intent.putExtra("ITEM_ID", currentProduct?.id ?: -1)
            intent.putExtra("LENDER_ID", currentProduct?.userId ?: -1)
            intent.putExtra("SELLER_NICKNAME", currentProduct?.sellerNickname)
            // intent.putExtra("IMAGE_URL", currentProduct?.imageUrls?.firstOrNull())

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

        // 2. 이미지 슬라이더
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

        markReservedOnCalendar(product.reservedPeriods ?: emptyList())

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
                    val roomId = mapData?.get("roomId")?.toString()

                    if (roomId != null) {
                        val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                        intent.putExtra("ROOM_ID", roomId)
                        intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                        intent.putExtra("PRODUCT_ID", product.id?.toInt())
                        intent.putExtra("PRODUCT_TITLE", product.title)
                        intent.putExtra("PRODUCT_PRICE", product.price)
                        intent.putExtra("PRODUCT_DEPOSIT", product.deposit ?: 0)
                        intent.putExtra("LENDER_ID", product.userId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ProductDetailActivity, "채팅방 입장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@ProductDetailActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun markReservedOnCalendar(periods: List<String>) {
        if (periods.isEmpty()) {
            binding.textReservedPeriods.visibility = View.GONE
            return
        }

        val reservedDays = mutableSetOf<Long>()

        for (range in periods) {
            val parts = range.split("~")
            if (parts.size != 2) continue
            val start = runCatching { dayFormat.parse(parts[0]) }.getOrNull()
            val end = runCatching { dayFormat.parse(parts[1]) }.getOrNull()
            if (start != null && end != null) {
                val cal = java.util.Calendar.getInstance().apply {
                    time = start
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val endCal = java.util.Calendar.getInstance().apply {
                    time = end
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }

                while (!cal.after(endCal)) {
                    reservedDays.add(cal.timeInMillis)
                    cal.add(java.util.Calendar.DATE, 1)
                }
            }
        }

        binding.textReservedPeriods.visibility = View.VISIBLE
        binding.textReservedPeriods.text = "대여중: ${periods.joinToString(", ")}"

        binding.calendarAvailability.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = java.util.Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (reservedDays.contains(cal.timeInMillis)) {
                Toast.makeText(this, "이미 대여된 날짜입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
