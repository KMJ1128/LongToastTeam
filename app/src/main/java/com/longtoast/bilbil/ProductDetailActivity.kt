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
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.adapter.DetailImageAdapter
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityProductDetailBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductDTO
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat
import java.time.LocalDate

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

        loadProductDetail(itemId)
        loadRentalSchedules(itemId.toLong())
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map
        currentProduct?.let { addMarkerAndMove(it) }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnStartChat.setOnClickListener { startChatting() }

        binding.btnCart.setOnClickListener {
            addToCart()
        }

        binding.btnRent.setOnClickListener {
            val p = currentProduct ?: return@setOnClickListener

            val myId = AuthTokenManager.getUserId()
            if (myId == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, RentRequestActivity::class.java).apply {
                putExtra("TITLE", p.title)
                putExtra("PRICE", p.price)
                putExtra("PRICE_UNIT", p.price_unit)
                putExtra("DEPOSIT", p.deposit ?: 0)
                putExtra("ITEM_ID", p.id)
                putExtra("LENDER_ID", p.userId)
                putExtra("SELLER_NICKNAME", p.sellerNickname)
                putExtra("IMAGE_URL", p.imageUrls?.firstOrNull())
                putExtra("BORROWER_ID", myId)
            }
            startActivity(intent)
        }
    }

    private fun addToCart() {
        val product = currentProduct
        if (product == null) {
            Toast.makeText(this, "상품 정보를 아직 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        CartManager.addItem(product)
        playAddToCartAnimation()
    }

    private fun playAddToCartAnimation() {
        binding.lottieAddCart.apply {
            visibility = View.VISIBLE
            playAnimation()

            addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    visibility = View.GONE
                    Toast.makeText(this@ProductDetailActivity, "장바구니에 담겼습니다.", Toast.LENGTH_SHORT).show()
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
        }
    }

    private fun loadProductDetail(itemId: Int) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService().getProductDetail(itemId)
                if (response.isSuccessful && response.body() != null) {
                    val raw = response.body()!!.data
                    val product = Gson().fromJson(Gson().toJson(raw), ProductDTO::class.java)
                    currentProduct = product
                    Log.d("ProductDetail", "sellerProfileImageUrl from API = ${product.sellerProfileImageUrl}")
                    updateUI(product)
                    naverMap?.let { addMarkerAndMove(product) }
                } else {
                    Toast.makeText(this@ProductDetailActivity, "상품 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ProductDetail", "Load Error", e)
                Toast.makeText(this@ProductDetailActivity, "상품 정보를 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(product: ProductDTO) {
        binding.textTitle.text = product.title

        val timeAgo = TimeAgoUtils.formatKorean(product.createdAt)
        val categoryLabel = product.category ?: "기타"
        binding.textCategoryTime.text = "$categoryLabel · $timeAgo"

        binding.textDescription.text = product.description ?: ""

        val priceStr = numberFormat.format(product.price)
        val unit = PriceUnitMapper.toLabel(product.price_unit)
        binding.textPrice.text = "$priceStr 원 / $unit"

        val deposit = product.deposit ?: 0
        binding.textDeposit.text =
            if (deposit > 0) "보증금 ${numberFormat.format(deposit)}원" else "(보증금 없음)"

        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"

        val creditScore = product.sellerCreditScore ?: 0
        binding.textSellerAddress.text = "신용점수 ${creditScore}점"

        val fullTradeAddress = product.tradeLocation ?: product.address ?: "거래 위치 정보 없음"
        binding.textTradeAddress.text = fullTradeAddress

        // 🔥 프로필 이미지: resolve() 쓰지 말고 그대로
        val profileUrl = product.sellerProfileImageUrl
        Log.d("ProductDetail", "Glide load profileUrl = $profileUrl")

        if (!profileUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(profileUrl)
                .placeholder(R.drawable.no_profile)
                .error(R.drawable.no_profile)
                .circleCrop()
                .into(binding.imageSellerProfile)
        } else {
            binding.imageSellerProfile.setImageResource(R.drawable.no_profile)
        }

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

    private fun loadRentalSchedules(itemId: Long) {
        RetrofitClient.getApiService()
            .getRentalSchedules(itemId)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    val raw = response.body()?.data ?: return
                    val type = object : TypeToken<List<Map<String, String>>>() {}.type
                    val schedules: List<Map<String, String>> =
                        Gson().fromJson(Gson().toJson(raw), type)

                    applyCalendarBlackout(schedules)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("ProductDetail", "loadRentalSchedules failed", t)
                }
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

    inner class BlackoutDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

        override fun decorate(view: DayViewFacade) {
            view.setBackgroundDrawable(
                resources.getDrawable(R.drawable.calendar_blackout_bg, null)
            )
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

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("ProductDetail", "createChatRoom failed", t)
                    Toast.makeText(this@ProductDetailActivity, "채팅방 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
