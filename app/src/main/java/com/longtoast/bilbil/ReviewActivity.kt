package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ReviewCreateRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ReviewActivity : AppCompatActivity() {

    private lateinit var ratingBar: RatingBar
    private lateinit var editContent: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var toolbar: MaterialToolbar

    // Intent로 받아와야 하는 값
    private var transactionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        // 1. Intent 데이터 수신
        transactionId = intent.getIntExtra("TRANSACTION_ID", -1)

        if (transactionId == -1) {
            Toast.makeText(this, "잘못된 접근입니다. (거래 ID 없음)", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        ratingBar = findViewById(R.id.rating_bar)
        editContent = findViewById(R.id.edit_review_content)
        btnSubmit = findViewById(R.id.btn_submit_review)
        toolbar = findViewById(R.id.toolbar_review)
    }

    private fun setupListeners() {
        // 뒤로가기 버튼
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 작성 완료 버튼
        btnSubmit.setOnClickListener {
            submitReview()
        }
    }

    private fun submitReview() {
        val rating = ratingBar.rating.toInt()
        val comment = editContent.text.toString().trim()
        val reviewerId = AuthTokenManager.getUserId()

        // 1. 유효성 검사
        if (reviewerId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rating < 1) {
            Toast.makeText(this, "최소 1점 이상의 별점을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. DTO 생성
        val request = ReviewCreateRequest(
            transactionId = transactionId.toLong(),
            rating = rating,
            comment = comment
        )

        // 3. 서버 전송
        RetrofitClient.getApiService().createReview(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        // ✅ [수정] 작성 성공 시 -> "내가 쓴 리뷰" 목록으로 이동
                        Toast.makeText(this@ReviewActivity, "리뷰가 등록되었습니다!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@ReviewActivity, ReviewListActivity::class.java).apply {
                            // 내가 쓴 리뷰 목록 보기 모드로 실행
                            putExtra("REVIEW_TYPE", "WRITTEN")
                            // 뒤로가기 스택 정리 (리뷰 작성 화면으로 다시 돌아오지 않게)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish() // 작성 화면 종료
                        return
                    }

                    // ❌ 실패 케이스 처리
                    val code = response.code()
                    val errorMsg = try {
                        val errJson = response.errorBody()?.string()
                        if (!errJson.isNullOrEmpty()) {
                            com.google.gson.Gson().fromJson(errJson, MsgEntity::class.java)?.message
                        } else null
                    } catch (e: Exception) {
                        null
                    }

                    if (code == 400 && errorMsg == "한 거래 당 리뷰는 1개씩 등록 가능합니다.") {
                        Toast.makeText(this@ReviewActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ReviewActivity, "리뷰 등록 실패 (코드: $code)", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("REVIEW", "통신 오류", t)
                    Toast.makeText(this@ReviewActivity, "서버 연결 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}