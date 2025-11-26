package com.longtoast.bilbil

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.databinding.ItemCartBinding
import com.longtoast.bilbil.dto.ProductDTO
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

            // 이미지 로드 (Base64)
            val images = product.imageUrls
            if (!images.isNullOrEmpty()) {
                try {
                    val cleanBase64 = images[0].substringAfter("base64,")
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.imageCartItem.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    binding.imageCartItem.setImageResource(R.drawable.ic_default_category)
                }
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
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}