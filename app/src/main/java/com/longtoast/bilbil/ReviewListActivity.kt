// com.longtoast.bilbil.ReviewListActivity.kt
package com.longtoast.bilbil

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

    // "WRITTEN"(내가 쓴) / "RECEIVED"(내가 받은) / "SELLER"(특정 판매자)
    private var reviewType: String = "WRITTEN"
    private var sellerId: Int = -1
    private var sellerNickname: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent로 타입 받기
        reviewType = intent.getStringExtra("REVIEW_TYPE") ?: "WRITTEN"
        sellerId = intent.getIntExtra("SELLER_ID", -1)
        sellerNickname = intent.getStringExtra("SELLER_NICKNAME")

        setupUI()
        fetchReviews()
    }

    private fun setupUI() {
        // 툴바 제목 설정
        val title = when (reviewType) {
            "WRITTEN" -> "내가 쓴 리뷰"
            "RECEIVED" -> "내가 받은 리뷰"
            "SELLER" -> sellerNickname?.let { "$it 님의 리뷰" } ?: "판매자 리뷰"
            else -> "리뷰 목록"
        }
        binding.toolbarTitle.text = title

        binding.btnBack.setOnClickListener { finish() }

        // ⭐ 어댑터에 reviewType 전달
        adapter = ReviewListAdapter(emptyList(), reviewType)
        binding.recyclerReviewList.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviewList.adapter = adapter
    }

    private fun fetchReviews() {
        binding.progressBar.visibility = View.VISIBLE

        val apiService = RetrofitClient.getApiService()

        val call: Call<MsgEntity> = when (reviewType) {
            "WRITTEN" -> {
                apiService.getMyWrittenReviews() // GET /reviews/my
            }
            "RECEIVED" -> {
                apiService.getMyReceivedReviews() // GET /reviews/received
            }
            "SELLER" -> {
                if (sellerId == -1) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "판매자 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return
                }
                apiService.getSellerReviews(sellerId) // GET /reviews/seller/{sellerId}
            }
            else -> {
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
