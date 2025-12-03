// com.longtoast.bilbil.ReviewActivity.kt
package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
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
    private lateinit var btnClose: ImageButton

    // 🔥 Long 으로 통일
    private var transactionId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        // 1. Intent 데이터 수신
        //    - 예전 flow: Int 로 넣었을 수도 있으니 둘 다 지원
        val intId = intent.getIntExtra("TRANSACTION_ID", -1)
        transactionId = if (intId != -1) {
            intId.toLong()
        } else {
            intent.getLongExtra("TRANSACTION_ID", -1L)
        }

        Log.d("ReviewActivity", "받은 TRANSACTION_ID = $transactionId")

        if (transactionId <= 0L) {
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
        btnClose = findViewById(R.id.btn_close)
    }

    private fun setupListeners() {
        btnClose.setOnClickListener { finish() }

        btnSubmit.setOnClickListener {
            submitReview()
        }
    }

    private fun submitReview() {
        val rating = ratingBar.rating.toInt()
        val comment = editContent.text.toString().trim()
        val reviewerId = AuthTokenManager.getUserId()

        if (reviewerId == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rating < 1) {
            Toast.makeText(this, "최소 1점 이상의 별점을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (transactionId <= 0L) {
            Toast.makeText(this, "유효하지 않은 거래 ID입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 서버에 보낼 DTO
        val request = ReviewCreateRequest(
            transactionId = transactionId,
            rating = rating,
            comment = comment
        )

        Log.d("ReviewActivity", "submitReview() call: txId=$transactionId, rating=$rating")

        RetrofitClient.getApiService().createReview(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@ReviewActivity,
                            "리뷰가 등록되었습니다!",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(
                            this@ReviewActivity,
                            ReviewListActivity::class.java
                        ).apply {
                            putExtra("REVIEW_TYPE", "WRITTEN")
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                        return
                    }

                    val code = response.code()
                    val errorMsg = try {
                        val errJson = response.errorBody()?.string()
                        if (!errJson.isNullOrEmpty()) {
                            Gson().fromJson(errJson, MsgEntity::class.java)?.message
                        } else null
                    } catch (e: Exception) {
                        null
                    }

                    if (code == 400 && errorMsg == "한 거래 당 리뷰는 1개씩 등록 가능합니다.") {
                        Toast.makeText(this@ReviewActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this@ReviewActivity,
                            "리뷰 등록 실패 (코드: $code)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("REVIEW", "통신 오류", t)
                    Toast.makeText(
                        this@ReviewActivity,
                        "서버 연결 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}
