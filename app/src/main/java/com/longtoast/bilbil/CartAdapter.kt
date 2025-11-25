package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R
import com.longtoast.bilbil.databinding.ItemCartBinding
import com.longtoast.bilbil.dto.ProductDTO
import com.longtoast.bilbil.util.RemoteImageLoader
import java.text.DecimalFormat

class CartAdapter(
    private val items: MutableList<ProductDTO>,
    private val onItemRemoved: () -> Unit // 아이템 삭제 시 총액 갱신을 위한 콜백
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val numberFormat = DecimalFormat("#,###")

    inner class CartViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductDTO, position: Int) {
            binding.textCartTitle.text = product.title
            binding.textCartPrice.text = "${numberFormat.format(product.price)}원"

            val firstImage = product.imageUrls?.firstOrNull()
            RemoteImageLoader.load(binding.imageCartItem, firstImage, R.drawable.ic_default_category)

            // 삭제 버튼 클릭 시
            binding.btnDeleteItem.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                // 내부 리스트와 매니저 데이터를 모두 제거하여 UI와 데이터의 일관성을 유지
                CartManager.removeItem(currentPosition)
                items.removeAt(currentPosition)
                notifyItemRemoved(currentPosition)
                notifyItemRangeChanged(currentPosition, itemCount - currentPosition)
                onItemRemoved() // 액티비티에 알림 (총액 갱신용)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}