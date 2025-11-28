package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.databinding.ItemCartBinding
import com.longtoast.bilbil.dto.ProductDTO
import java.text.DecimalFormat

class CartAdapter(
    private val items: MutableList<ProductDTO>,
    private val onItemRemoved: () -> Unit // 아이템 삭제 시 총액 갱신을 위한 콜백
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val numberFormat = DecimalFormat("#,###")

    inner class CartViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductDTO) {
            binding.textCartTitle.text = product.title

            // 주소
            binding.textCartLocation.text = product.address ?: product.tradeLocation ?: "위치 미정"

            // 가격
            binding.textCartPrice.text = "₩ ${numberFormat.format(product.price)}"

            // 🔥 [추가] 상태 표시 (AVAILABLE / RENTED)
            val isAvailable = product.status == "AVAILABLE"
            binding.textCartStatus.text = if (isAvailable) "대여 가능" else "대여중"
            binding.textCartStatus.setBackgroundResource(
                if (isAvailable) R.drawable.badge_available else R.drawable.badge_rented
            )

            // 🔥 [추가] 보증금 표시
            val deposit = product.deposit ?: 0
            if (deposit > 0) {
                binding.textCartDeposit.text = "보증금 ₩ ${numberFormat.format(deposit)}"
                binding.textCartDeposit.visibility = View.VISIBLE
            } else {
                binding.textCartDeposit.visibility = View.GONE
            }

            // 이미지 로드
            val fullUrl = ImageUrlUtils.resolve(product.imageUrls?.firstOrNull())
            Glide.with(binding.root.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(binding.imageCartItem)

            // 🔥 [수정] 삭제 버튼 클릭 시 즉시 반영
            binding.btnDeleteItem.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // 1. 어댑터 내부 리스트에서 삭제 (이게 중요! 화면 즉시 반영용)
                    items.removeAt(position)

                    // 2. 실제 데이터 매니저(싱글톤)에서 삭제
                    CartManager.removeItem(position)

                    // 3. RecyclerView에 삭제 알림 (애니메이션과 함께 사라짐)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, items.size)

                    // 4. 액티비티에 총액 갱신 알림
                    onItemRemoved()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}