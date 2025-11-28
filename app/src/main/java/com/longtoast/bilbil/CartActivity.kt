package com.longtoast.bilbil

import android.content.Intent
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

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val cartList = CartManager.getItems().toMutableList()

        adapter = CartAdapter(
            items = cartList,
            // ✅ [추가] 아이템 클릭 시 상세 페이지로 이동
            onItemClicked = { product ->
                val intent = Intent(this, ProductDetailActivity::class.java).apply {
                    putExtra("ITEM_ID", product.id)
                }
                startActivity(intent)
            },
            // 아이템 삭제 시 동작
            onItemRemoved = {
                if (CartManager.getItems().isEmpty()) {
                    Toast.makeText(this, "장바구니가 비워졌습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            setupRecyclerView()
        }
    }
}