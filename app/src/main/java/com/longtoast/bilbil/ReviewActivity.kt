// com.longtoast.bilbil.ReviewActivity.kt
package com.longtoast.bilbil

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

        // 1. Intent 데이터 수신 (이전 화면에서 putExtra로 넘겨줘야 함)
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
        val reviewerId = AuthTokenManager.getUserId()  // 서버에 안 보내지만 로그인 체크용으로 사용

        // 1. 유효성 검사
        if (reviewerId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rating < 1) {
            Toast.makeText(this, "최소 1점 이상의 별점을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. DTO 생성 (🔥 reviewerId는 서버에서 JWT로 가져가므로 안 보냄)
        val request = ReviewCreateRequest(
            transactionId = transactionId.toLong(),  // Int → Long 변환
            rating = rating,
            comment = comment
        )

        // 3. 서버 전송
        RetrofitClient.getApiService().createReview(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Log.d("REVIEW", "리뷰 작성 성공")
                        Toast.makeText(
                            this@ReviewActivity,
                            "소중한 리뷰가 등록되었습니다!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish() // 작성 후 화면 닫기
                    } else {
                        Log.e(
                            "REVIEW",
                            "작성 실패: ${response.code()} ${response.errorBody()?.string()}"
                        )
                        Toast.makeText(
                            this@ReviewActivity,
                            "리뷰 등록 실패: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("REVIEW", "통신 오류", t)
                    Toast.makeText(
                        this@ReviewActivity,
                        "서버 연결 오류가 발생했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}
