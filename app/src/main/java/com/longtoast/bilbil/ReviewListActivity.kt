package com.longtoast.bilbil

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityReviewListBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ReviewDTO
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReviewListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewListBinding
    private lateinit var adapter: ReviewListAdapter

    // 화면 모드: "MY_WRITTEN" / "MY_RECEIVED" / "SELLER"
    private var reviewType: String = "MY_WRITTEN"

    // SELLER 모드(판매자 프로필에서 볼 때)만 사용하는 값
    private var sellerId: Int = -1
    private var sellerNickname: String? = null

    // 탭으로 선택된 역할: "LENDER" / "BORROWER"
    private var currentRole: String = "LENDER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent로 타입 받기
        reviewType = intent.getStringExtra("REVIEW_TYPE") ?: "MY_WRITTEN"
        sellerId = intent.getIntExtra("SELLER_ID", -1)
        sellerNickname = intent.getStringExtra("SELLER_NICKNAME")

        setupUI()
        fetchReviews()
    }

    private fun setupUI() {
        // 툴바 제목 설정
        val title = when (reviewType) {
            "MY_WRITTEN" -> "내가 쓴 리뷰"
            "MY_RECEIVED" -> "내가 받은 리뷰"
            "SELLER" -> sellerNickname?.let { "$it 님의 리뷰" } ?: "판매자 리뷰"
            else -> "리뷰 목록"
        }
        binding.toolbarTitle.text = title
        binding.btnBack.setOnClickListener { finish() }

        // ⭐ 어댑터에 넘겨줄 타입은 기존 주석 유지 차원에서 변환
        val adapterType = when (reviewType) {
            "MY_WRITTEN" -> "WRITTEN"
            "MY_RECEIVED" -> "RECEIVED"
            else -> reviewType
        }

        adapter = ReviewListAdapter(emptyList(), adapterType)
        binding.recyclerReviewList.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviewList.adapter = adapter

        // 🔥 탭 레이아웃 설정 (내가 쓴/받은 리뷰일 때만 사용)
        if (reviewType == "MY_WRITTEN" || reviewType == "MY_RECEIVED") {
            setupTabs()
        } else {
            binding.tabLayoutRole.visibility = View.GONE
        }
    }

    private fun setupTabs() {
        val tabLayout: TabLayout = binding.tabLayoutRole
        tabLayout.visibility = View.VISIBLE
        tabLayout.removeAllTabs()

        // 0번 탭: 대여자로서, 1번 탭: 사용자로서
        tabLayout.addTab(tabLayout.newTab().setText("대여자로서"))
        tabLayout.addTab(tabLayout.newTab().setText("사용자로서"))

        currentRole = "LENDER" // 기본은 대여자 탭

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentRole = if (tab?.position == 0) "LENDER" else "BORROWER"
                fetchReviews()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 같은 탭 다시 눌러도 새로고침
                fetchReviews()
            }
        })
    }

    private fun fetchReviews() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE

        val apiService = RetrofitClient.getApiService()

        val call: Call<MsgEntity> = when (reviewType) {
            // ✅ 내가 쓴 리뷰
            "MY_WRITTEN" -> {
                if (currentRole == "LENDER") {
                    // 대여자로서 쓴 리뷰
                    apiService.getMyWrittenReviewsAsSeller()
                } else {
                    // 사용자로서 쓴 리뷰
                    apiService.getMyWrittenReviewsAsBorrower()
                }
            }

            // ✅ 내가 받은 리뷰
            "MY_RECEIVED" -> {
                if (currentRole == "LENDER") {
                    // 대여자로서 받은 리뷰
                    apiService.getMyReceivedReviewsAsSeller()
                } else {
                    // 사용자로서 받은 리뷰
                    apiService.getMyReceivedReviewsAsBorrower()
                }
            }

            // ✅ 특정 판매자 리뷰 (탭 없음)
            "SELLER" -> {
                if (sellerId == -1) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "판매자 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return
                }
                apiService.getSellerReviews(sellerId)
            }

            else -> {
                // 혹시 모르는 fallback
                apiService.getMyWrittenReviews()
            }
        }

        call.enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val rawData = response.body()?.data
                    if (rawData != null) {
                        try {
                            val gson = Gson()
                            val type = object : TypeToken<List<ReviewDTO>>() {}.type
                            val list: List<ReviewDTO> = gson.fromJson(gson.toJson(rawData), type)

                            if (list.isEmpty()) {
                                binding.textEmpty.visibility = View.VISIBLE
                                adapter.updateList(emptyList())
                            } else {
                                binding.textEmpty.visibility = View.GONE
                                adapter.updateList(list)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@ReviewListActivity, "데이터 처리 오류", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        binding.textEmpty.visibility = View.VISIBLE
                        adapter.updateList(emptyList())
                    }
                } else {
                    Toast.makeText(this@ReviewListActivity, "리뷰 불러오기 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ReviewListActivity, "서버 연결 오류", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
