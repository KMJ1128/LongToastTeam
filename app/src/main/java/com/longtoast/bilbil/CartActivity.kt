package com.longtoast.bilbil

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.ActivityCartBinding

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var adapter: CartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기 버튼
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // CartManager에서 데이터를 가져와서 어댑터 생성
        val cartList = CartManager.getItems().toMutableList()

        adapter = CartAdapter(cartList) {
            // 아이템이 삭제되었을 때 실행될 로직
            if (CartManager.getItems().isEmpty()) {
                Toast.makeText(this, "장바구니가 비워졌습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter
    }

    // 화면에 다시 돌아왔을 때 갱신 (선택사항)
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            setupRecyclerView()
        }
    }
}