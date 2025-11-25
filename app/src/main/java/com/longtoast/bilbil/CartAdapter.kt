package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.databinding.ItemCartBinding
import com.longtoast.bilbil.dto.ProductDTO
import java.text.DecimalFormat
import com.bumptech.glide.Glide
import com.longtoast.bilbil.R
import com.longtoast.bilbil.util.ImageUrlUtils

class CartAdapter(
    private val items: MutableList<ProductDTO>,
    private val onItemRemoved: () -> Unit // 아이템 삭제 시 총액 갱신을 위한 콜백
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val numberFormat = DecimalFormat("#,###")

    inner class CartViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: ProductDTO) {
            binding.textCartTitle.text = product.title
            binding.textCartPrice.text = "${numberFormat.format(product.price)}원"

            val images = ImageUrlUtils.buildFullUrls(product.imageUrls)
            if (images.isNotEmpty()) {
                Glide.with(binding.imageCartItem.context)
                    .load(images[0])
                    .placeholder(R.drawable.ic_default_category)
                    .error(R.drawable.ic_default_category)
                    .into(binding.imageCartItem)
            } else {
                binding.imageCartItem.setImageResource(R.drawable.ic_default_category)
            }

            // 삭제 버튼 클릭 시
            binding.btnDeleteItem.setOnClickListener {
                CartManager.removeItem(adapterPosition) // 매니저에서 삭제
                notifyItemRemoved(adapterPosition) // 리스트 갱신
                onItemRemoved() // 액티비티에 알림 (총액 갱신용)
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