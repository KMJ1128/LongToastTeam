package com.longtoast.bilbil

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.longtoast.bilbil.databinding.ActivityCartBinding
import java.text.DecimalFormat

class CartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCartBinding
    private lateinit var adapter: CartAdapter
    private val numberFormat = DecimalFormat("#,###")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        updateTotalAmount()

        // 결제(대여) 버튼
        binding.btnCheckout.setOnClickListener {
            if (CartManager.getItems().isEmpty()) {
                Toast.makeText(this, "장바구니가 비어있습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "총 ${CartManager.getItems().size}건 대여를 요청합니다.", Toast.LENGTH_SHORT).show()
                // 여기서 실제 대여 로직 진행 후 CartManager.clear() 호출 가능
            }
        }
    }

    private fun setupRecyclerView() {
        // CartManager에서 데이터를 가져와서 어댑터 생성
        // toMutableList()를 써서 복사본을 넘겨줌 (화면 갱신 안전성 위함)
        val cartList = CartManager.getItems().toMutableList()

        adapter = CartAdapter(cartList) {
            // 아이템이 삭제될 때마다 총액 갱신
            updateTotalAmount()

            // 만약 다 지워서 비어있으면 안내 메시지 처리 등 가능
            if (CartManager.getItems().isEmpty()) {
                Toast.makeText(this, "장바구니가 비워졌습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerCart.layoutManager = LinearLayoutManager(this)
        binding.recyclerCart.adapter = adapter
    }

    private fun updateTotalAmount() {
        val total = CartManager.getTotalPrice()
        binding.textTotalAmount.text = "${numberFormat.format(total)}원"
    }

    // 화면에 다시 돌아왔을 때 갱신 (선택사항)
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            setupRecyclerView() // 데이터가 바뀌었을 수 있으므로 다시 로드
            updateTotalAmount()
        }
    }
}