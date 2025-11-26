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
import com.kakao.vectormap.MapView
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

// ✅ Kakao Open Map import
import net.daum.mf.map.api.MapView
import net.daum.mf.map.api.MapPoint
import net.daum.mf.map.api.MapPOIItem

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var currentProduct: ProductDTO? = null
    private val numberFormat = DecimalFormat("#,###")

    // ✅ 미니 지도 뷰
    private var mapViewMini: MapView? = null

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
        binding.btnShare.setOnClickListener {
            Toast.makeText(this, "공유하기", Toast.LENGTH_SHORT).show()
        }
        binding.btnMore.setOnClickListener {
            Toast.makeText(this, "더보기", Toast.LENGTH_SHORT).show()
        }

        // 1. 채팅하기 버튼
        binding.btnStartChat.setOnClickListener { startChatting() }

        // 2. 장바구니 버튼
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
            val intent = Intent(this, RentRequestActivity::class.java).apply {
                putExtra("TITLE", binding.textTitle.text.toString())
                putExtra("PRICE", currentProduct?.price ?: 0)
                putExtra("DEPOSIT", currentProduct?.deposit ?: 0)
                putExtra("ITEM_ID", currentProduct?.id ?: -1)
                putExtra("LENDER_ID", currentProduct?.userId ?: -1)
                putExtra("SELLER_NICKNAME", currentProduct?.sellerNickname)
            }
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
        // 1. 텍스트 정보 바인딩
        binding.textTitle.text = product.title
        binding.textCategoryTime.text = "${product.category ?: "기타"} · 1분 전"
        binding.textDescription.text = product.description ?: ""

        // 가격 및 보증금
        val priceStr = numberFormat.format(product.price)
        binding.textPrice.text = "$priceStr 원"

        val deposit = product.deposit ?: 0
        binding.textDeposit.text = if (deposit > 0) {
            "보증금 ${numberFormat.format(deposit)}원"
        } else {
            "(보증금 없음)"
        }

        // 판매자 정보
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

        // 3. 거래 위치 텍스트
        binding.textTradeLocation.text =
            product.tradeLocation ?: product.address ?: "거래 위치 정보 없음"

        // 4. 미니 카카오 지도 설정
        setupMiniMap(product)
    }

    /**
     * ✅ Open Map SDK를 사용하는 미니 지도 설정
     */
    private fun setupMiniMap(product: ProductDTO) {
        // 우선 mapView 객체 생성 (한 번만)
        if (mapViewMini == null) {
            mapViewMini = MapView(this)
            binding.layoutLocationMap.addView(
                mapViewMini,
                MapView.LayoutParams(
                    MapView.LayoutParams.MATCH_PARENT,
                    MapView.LayoutParams.MATCH_PARENT
                )
            )
        }

        val mapView = mapViewMini ?: return

        // 🔹 여기서 실제 좌표를 넣어줘야 함
        // product에 위도/경도 필드가 있다면 그걸 사용하고,
        // 지금은 예시로 "서울 시청" 근처 좌표를 임시로 사용
        val lat = 37.5662952
        val lng = 126.9779451

        val point = MapPoint.mapPointWithGeoCoord(lat, lng)

        // 지도 중심 및 줌 레벨
        mapView.setMapCenterPoint(point, true)
        mapView.setZoomLevel(3, false)

        // 마커 추가
        val marker = MapPOIItem().apply {
            itemName = "거래 위치"
            mapPoint = point
            markerType = MapPOIItem.MarkerType.BluePin
            selectedMarkerType = MapPOIItem.MarkerType.RedPin
        }
        mapView.removeAllPOIItems()
        mapView.addPOIItem(marker)
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

        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        val rawData = response.body()?.data
                        val gson = Gson()
                        val type = object : TypeToken<Map<String, Any>>() {}.type
                        val mapData: Map<String, Any>? =
                            gson.fromJson(gson.toJson(rawData), type)
                        val roomId = mapData?.get("roomId")?.toString()

                        if (roomId != null) {
                            val intent =
                                Intent(this@ProductDetailActivity, ChatRoomActivity::class.java)
                            intent.putExtra("ROOM_ID", roomId)
                            intent.putExtra("SELLER_NICKNAME", product.sellerNickname)
                            startActivity(intent)
                        } else {
                            Toast.makeText(
                                this@ProductDetailActivity,
                                "채팅방 입장에 실패했습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@ProductDetailActivity,
                            "오류가 발생했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
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

    override fun onDestroy() {
        super.onDestroy()
        // 지도 리소스 정리 (있으면)
        mapViewMini = null
    }
}
