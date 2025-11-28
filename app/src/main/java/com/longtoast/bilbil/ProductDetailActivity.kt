package com.longtoast.bilbil

import android.animation.Animator
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
import com.prolificinteractive.materialcalendarview.*
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat
import java.time.LocalDate

// 네이버 지도 관련 import
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.CameraUpdate
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage

class ProductDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: ProductDTO? = null
    private val numberFormat = DecimalFormat("#,###")

    private lateinit var mapView: MapView
    private var naverMap: NaverMap? = null
    private val marker = Marker()

    // 대여된 날짜 저장 리스트
    private val blackoutDates = mutableListOf<CalendarDay>()

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

        mapView = binding.detailMapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        setupListeners()

        // 🚨 오류가 났던 부분: 아래 함수 정의가 클래스 내부에 잘 있는지 확인하세요.
        loadProductDetail(itemId)
        loadRentalSchedules(itemId.toLong())
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map
        currentProduct?.let { addMarkerAndMove(it) }
    }

    // -------------------------------------------------------------
    // 리스너 설정
    // -------------------------------------------------------------
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnStartChat.setOnClickListener { startChatting() }

        // 장바구니 버튼 리스너
        binding.btnCart.setOnClickListener {
            addToCart()
        }

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
    }

    // -------------------------------------------------------------
    // 장바구니 & 애니메이션 로직
    // -------------------------------------------------------------
    private fun addToCart() {
        val product = currentProduct
        if (product == null) {
            Toast.makeText(this, "상품 정보를 아직 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 매니저에 상품 추가
        CartManager.addItem(product)

        // 2. Lottie 애니메이션 재생
        playAddToCartAnimation()
    }

    private fun playAddToCartAnimation() {
        binding.lottieAddCart.apply {
            visibility = View.VISIBLE
            playAnimation()

            addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    Toast.makeText(this@ProductDetailActivity, "장바구니에 담겼습니다.", Toast.LENGTH_SHORT).show()
                }

                override fun onAnimationCancel(animation: Animator) {
                    visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
    }

    // -------------------------------------------------------------
    // 서버 통신 로직 (상품 상세 정보 불러오기)
    // -------------------------------------------------------------
    private fun loadProductDetail(itemId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().getProductDetail(itemId)
                if (response.isSuccessful && response.body() != null) {
                    val raw = response.body()!!.data
                    val product = Gson().fromJson(Gson().toJson(raw), ProductDTO::class.java)
                    currentProduct = product
                    updateUI(product)
                    naverMap?.let { addMarkerAndMove(product) }
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

        val priceStr = numberFormat.format(product.price)
        val unit = PriceUnitMapper.toLabel(product.price_unit)
        binding.textPrice.text = "$priceStr 원 / $unit"

        val deposit = product.deposit ?: 0
        binding.textDeposit.text =
            if (deposit > 0) "보증금 ${numberFormat.format(deposit)}원" else "(보증금 없음)"

        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"
        binding.textSellerAddress.text = product.address ?: product.tradeLocation ?: "위치 미설정"

        val images = product.imageUrls?.mapNotNull { ImageUrlUtils.resolve(it) } ?: emptyList()
        if (images.isNotEmpty()) {
            binding.viewPagerImages.adapter = DetailImageAdapter(images)
            binding.textImageIndicator.text = "1 / ${images.size}"
            binding.viewPagerImages.registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        binding.textImageIndicator.text = "${position + 1} / ${images.size}"
                    }
                }
            )
        }
    }

    // -------------------------------------------------------------
    // 대여 일정 확인 로직
    // -------------------------------------------------------------
    private fun loadRentalSchedules(itemId: Long) {
        RetrofitClient.getApiService()
            .getRentalSchedules(itemId.toLong())
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val raw = response.body()?.data ?: return
                    val type = object : TypeToken<List<Map<String, String>>>() {}.type
                    val schedules: List<Map<String, String>> =
                        Gson().fromJson(Gson().toJson(raw), type)

                    applyCalendarBlackout(schedules)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {}
            })
    }

    private fun applyCalendarBlackout(schedules: List<Map<String, String>>) {
        blackoutDates.clear()

        for (range in schedules) {
            val start = LocalDate.parse(range["startDate"])
            val end = LocalDate.parse(range["endDate"])

            var date = start
            while (!date.isAfter(end)) {
                blackoutDates.add(
                    CalendarDay.from(date.year, date.monthValue, date.dayOfMonth)
                )
                date = date.plusDays(1)
            }
        }

        binding.calendarAvailability.addDecorator(BlackoutDecorator(blackoutDates))

        if (blackoutDates.isNotEmpty()) {
            binding.textReservedPeriods.visibility = View.VISIBLE
            binding.textReservedPeriods.text =
                "대여 불가: ${schedules.joinToString { "${it["startDate"]} ~ ${it["endDate"]}" }}"
        }
    }

    // -------------------------------------------------------------
    // 내부 클래스 및 기타 함수
    // -------------------------------------------------------------
    inner class BlackoutDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

        override fun decorate(view: DayViewFacade) {
            view.setBackgroundDrawable(resources.getDrawable(R.drawable.calendar_blackout_bg, null))
            view.addSpan(object : android.text.style.ForegroundColorSpan(0xFFFFFFFF.toInt()) {})
        }
    }

    private fun addMarkerAndMove(product: ProductDTO) {
        val map = naverMap ?: return
        val lat = product.latitude ?: 37.5665
        val lng = product.longitude ?: 126.9780

        val pos = LatLng(lat, lng)
        marker.position = pos
        marker.map = map
        marker.icon = OverlayImage.fromResource(R.drawable.ic_location_pin)
        map.moveCamera(CameraUpdate.scrollTo(pos))
    }

    private fun startChatting() {
        val myId = AuthTokenManager.getUserId()
        val p = currentProduct ?: return

        if (myId == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = ChatRoomCreateRequest(p.id, p.userId, myId)
        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val raw = response.body()?.data ?: return
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val map = Gson().fromJson<Map<String, Any>>(Gson().toJson(raw), type)
                    val roomId = map["roomId"]?.toString()

                    if (roomId != null) {
                        startActivity(
                            Intent(this@ProductDetailActivity, ChatRoomActivity::class.java).apply {
                                putExtra("ROOM_ID", roomId)
                                putExtra("SELLER_NICKNAME", p.sellerNickname)
                                putExtra("PRODUCT_ID", p.id)
                                putExtra("PRODUCT_TITLE", p.title)
                                putExtra("PRODUCT_PRICE", p.price)
                                putExtra("PRODUCT_DEPOSIT", p.deposit ?: 0)
                                putExtra("LENDER_ID", p.userId)
                                putExtra("PRICE_UNIT", p.price_unit)
                                putExtra("IMAGE_URL", p.imageUrls?.firstOrNull())
                            }
                        )
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {}
            })
    }

    // 네이버 지도 생명주기
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}