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

// --- Naver Map Imports ---
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
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

    // --- Naver Map Fields ---
    private lateinit var mapView: MapView
    private var naverMap: NaverMap? = null
    private val marker = Marker()

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

        // ⭐ 네이버 지도 뷰 연결
        mapView = binding.detailMapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        setupListeners()
        loadProductDetail(itemId)
    }

    // 네이버 지도 준비 완료
    override fun onMapReady(map: NaverMap) {
        naverMap = map

        // 지도 준비되면 상품 위치 찍기
        currentProduct?.let { addMarkerAndMove(it) }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "공유하기 기능 준비중", Toast.LENGTH_SHORT).show()
        }

        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "더보기 기능 준비중", Toast.LENGTH_SHORT).show()
        }

        binding.btnCart.setOnClickListener {
            currentProduct?.let { product ->
                CartManager.addItem(product)
                Toast.makeText(this, "장바구니에 담았습니다.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "상품 정보를 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
        }

        binding.btnRent.setOnClickListener {
            val intent = Intent(this, RentRequestActivity::class.java).apply {
                putExtra("TITLE", currentProduct?.title)
                putExtra("PRICE", currentProduct?.price ?: 0)
                putExtra("DEPOSIT", currentProduct?.deposit ?: 0)
                putExtra("ITEM_ID", currentProduct?.id ?: -1)
                putExtra("LENDER_ID", currentProduct?.userId ?: -1)
                putExtra("SELLER_NICKNAME", currentProduct?.sellerNickname)
            }
            startActivity(intent)
        }

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

                    // 지도 준비되었다면 좌표 적용
                    naverMap?.let { addMarkerAndMove(product) }
                } else {
                    Toast.makeText(
                        this@ProductDetailActivity,
                        "정보를 불러올 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
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
        val priceLabel = PriceUnitMapper.toLabel(product.price_unit)
        binding.textPrice.text = "${numberFormat.format(product.price)} 원 / $priceLabel"
        val priceUnitLabel = PriceUnitMapper.toLabel(product.price_unit)
        val priceStr = numberFormat.format(product.price)
        binding.textPrice.text = "$priceStr 원 / $priceUnitLabel"

        val deposit = product.deposit ?: 0
        binding.textDeposit.text = if (deposit > 0) "보증금 ${numberFormat.format(deposit)}원" else "(보증금 없음)"

        binding.textSellerNickname.text = product.sellerNickname ?: "알 수 없음"
        binding.textSellerAddress.text =
            product.address ?: product.tradeLocation ?: "위치 미설정"

        val images = product.imageUrls?.mapNotNull { ImageUrlUtils.resolve(it) } ?: emptyList()
        // 이미지 슬라이더
        val fixedImages = product.imageUrls?.mapNotNull { ImageUrlUtils.resolve(it) } ?: emptyList()

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
        } else {
            binding.textImageIndicator.visibility = View.GONE
        }

        markReservedOnCalendar(product.reservedPeriods ?: emptyList())
    }

    // 🔥 네이버 지도에 마커 찍고 카메라 이동
    private fun addMarkerAndMove(product: ProductDTO) {
        val map = naverMap ?: return
        val lat = product.latitude
        val lng = product.longitude

        Log.d("ProductDetailMap", "product lat/lng = $lat / $lng")

        // 좌표가 없으면 그냥 서울 보여주기
        if (lat == null || lng == null) {
            Log.w("ProductDetailMap", "위도/경도가 null이라 서울로 이동")
            val seoul = LatLng(37.5665, 126.9780)
            marker.position = seoul
            marker.map = map
            map.moveCamera(CameraUpdate.scrollTo(seoul))
            return
        }

        // 네이버 지도는 한국만 타일 있음 → 범위 밖이면 서울로 대체
        if (lat !in 30.0..45.0 || lng !in 120.0..135.0) {
            Log.w("ProductDetailMap", "한국 범위 밖 좌표: lat=$lat, lng=$lng → 서울로 대체")
            val seoul = LatLng(37.5665, 126.9780)
            marker.position = seoul
            marker.map = map
            map.moveCamera(CameraUpdate.scrollTo(seoul))
            return
        }

        val position = LatLng(lat, lng)

        marker.position = position
        marker.map = map
        marker.icon = OverlayImage.fromResource(R.drawable.ic_location_pin)

        map.moveCamera(CameraUpdate.scrollTo(position))
    }

    /** 🔵 채팅 시작 */
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

        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@ProductDetailActivity, "채팅방 생성 실패", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val rawData = response.body()?.data
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val mapData: Map<String, Any>? = gson.fromJson(gson.toJson(rawData), type)
                    val roomId = when (val raw = mapData?.get("roomId")) {
                        is Number -> raw.toInt()
                        is String -> raw.toIntOrNull()
                        else -> null
                    }

                        if (roomId != null) {
                            val intent = Intent(
                                this@ProductDetailActivity,
                                ChatRoomActivity::class.java
                            )
                            intent.putExtra("ROOM_ID", roomId)
                            intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                            startActivity(intent)
                        }
                    if (roomId != null) {
                        val intent = Intent(this@ProductDetailActivity, ChatRoomActivity::class.java).apply {
                            putExtra("ROOM_ID", roomId)
                            putExtra("SELLER_NICKNAME", product.sellerNickname)
                            putExtra("PRODUCT_ID", product.id?.toInt())
                            putExtra("PRODUCT_TITLE", product.title)
                            putExtra("PRODUCT_PRICE", product.price)
                            putExtra("PRODUCT_DEPOSIT", product.deposit ?: 0)
                            putExtra("LENDER_ID", product.userId)
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ProductDetailActivity, "채팅방 입장 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(
                        this@ProductDetailActivity,
                        "네트워크 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // --- Lifecycle ---
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    /** 🔵 예약된 날짜 달력 표시 */
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
