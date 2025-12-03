// com.longtoast.bilbil.LenderReviewTargetsActivity.kt
package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityReviewListBinding
import com.longtoast.bilbil.dto.LenderReviewTargetDTO
import com.longtoast.bilbil.dto.MsgEntity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LenderReviewTargetsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewListBinding
    private lateinit var adapter: LenderReviewTargetAdapter
    private var targets: List<LenderReviewTargetDTO> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 activity_review_list.xml 사용
        binding = ActivityReviewListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔹 상단 타이틀 & 뒤로가기 버튼 세팅
        binding.toolbarTitle.text = "내 물건 빌려간 사람 리뷰 쓰기"
        binding.btnBack.setOnClickListener { finish() }

        // 🔹 이 화면은 역할 탭(대여자/사용자) 필요 없음 → 숨김
        binding.tabLayoutRole.visibility = View.GONE

        // 🔹 RecyclerView & Adapter 연결
        adapter = LenderReviewTargetAdapter(targets) { target ->
            openReviewWriteScreen(target)
        }
        binding.recyclerReviewList.layoutManager = LinearLayoutManager(this)
        binding.recyclerReviewList.adapter = adapter

        // 🔹 데이터 로드
        loadTargets()
    }

    private fun loadTargets() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        binding.recyclerReviewList.visibility = View.GONE

        RetrofitClient.getApiService()
            .getLenderReviewTargets()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    binding.progressBar.visibility = View.GONE

                    if (!response.isSuccessful) {
                        Toast.makeText(
                            this@LenderReviewTargetsActivity,
                            "목록 조회 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                        showEmpty()
                        return
                    }

                    val raw = response.body()?.data ?: run {
                        showEmpty()
                        return
                    }

                    try {
                        val gson = Gson()
                        val type = object : TypeToken<List<LenderReviewTargetDTO>>() {}.type
                        targets = gson.fromJson(gson.toJson(raw), type)

                        if (targets.isEmpty()) {
                            showEmpty()
                        } else {
                            binding.textEmpty.visibility = View.GONE
                            binding.recyclerReviewList.visibility = View.VISIBLE
                            adapter.updateList(targets)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showEmpty()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@LenderReviewTargetsActivity,
                        "네트워크 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                    showEmpty()
                }
            })
    }

    private fun showEmpty() {
        binding.recyclerReviewList.visibility = View.GONE
        binding.textEmpty.visibility = View.VISIBLE
    }

    private fun openReviewWriteScreen(target: LenderReviewTargetDTO) {
        // ✅ Transaction.id 를 ReviewActivity로 넘김
        val intent = Intent(this, ReviewActivity::class.java).apply {
            putExtra("TRANSACTION_ID", target.rentalId)   // Long 그대로
            putExtra("BORROWER_NICKNAME", target.borrowerNickname)
            putExtra("ITEM_TITLE", target.itemTitle)
        }
        startActivity(intent)
    }
}
