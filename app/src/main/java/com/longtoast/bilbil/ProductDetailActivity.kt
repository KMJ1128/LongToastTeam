package com.longtoast.bilbil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.longtoast.bilbil.databinding.ActivityProductDetailBinding

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val itemId = intent.getIntExtra("ITEM_ID", -1)

        binding.textItemId.text = "상품 ID: $itemId"

        // 여기서 API 호출 → 상세정보 불러오면 됨
    }
}
