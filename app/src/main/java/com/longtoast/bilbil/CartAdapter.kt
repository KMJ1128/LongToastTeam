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
    private val onItemClicked: (ProductDTO) -> Unit, // ✅ [추가] 클릭 이벤트 콜백
    private val onItemRemoved: () -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val numberFormat = DecimalFormat("#,###")

    inner class CartViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductDTO) {
            binding.textCartTitle.text = product.title
            binding.textCartLocation.text = product.address ?: product.tradeLocation ?: "위치 미정"
            binding.textCartPrice.text = "₩ ${numberFormat.format(product.price)}"

            val isAvailable = product.status == "AVAILABLE"
            binding.textCartStatus.text = if (isAvailable) "대여 가능" else "대여중"
            binding.textCartStatus.setBackgroundResource(
                if (isAvailable) R.drawable.badge_available else R.drawable.badge_rented
            )

            val deposit = product.deposit ?: 0
            if (deposit > 0) {
                binding.textCartDeposit.text = "보증금 ₩ ${numberFormat.format(deposit)}"
                binding.textCartDeposit.visibility = View.VISIBLE
            } else {
                binding.textCartDeposit.visibility = View.GONE
            }

            val fullUrl = ImageUrlUtils.resolve(product.imageUrls?.firstOrNull())
            Glide.with(binding.root.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(binding.imageCartItem)

            // ✅ [추가] 아이템 전체 클릭 시 상세 페이지 이동 콜백 실행
            binding.root.setOnClickListener {
                onItemClicked(product)
            }

            // 삭제 버튼
            binding.btnDeleteItem.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items.removeAt(position)
                    CartManager.removeItem(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, items.size)
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