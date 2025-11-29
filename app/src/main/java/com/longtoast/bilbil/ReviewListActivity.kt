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

    // "WRITTEN"(내가 쓴) 또는 "RECEIVED"(내가 받은)
    private var reviewType: String = "WRITTEN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent로 타입 받기
        reviewType = intent.getStringExtra("REVIEW_TYPE") ?: "WRITTEN"

        setupUI()
        fetchReviews()
    }

    private fun setupUI() {
        binding.toolbarTitle.text = if (reviewType == "WRITTEN") "내가 쓴 리뷰" else "내가 받은 리뷰"
        binding.btnBack.setOnClickListener { finish() }

        adapter = ReviewListAdapter(emptyList())
        binding.recyclerReviewList.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviewList.adapter = adapter
    }

    private fun fetchReviews() {
        binding.progressBar.visibility = View.VISIBLE

        val apiService = RetrofitClient.getApiService()
        val call = if (reviewType == "WRITTEN") {
            apiService.getMyWrittenReviews() // GET /reviews/my
        } else {
            apiService.getMyReceivedReviews() // GET /reviews/received
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