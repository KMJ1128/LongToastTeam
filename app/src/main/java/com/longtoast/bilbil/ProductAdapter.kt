package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.databinding.ItemProductListBinding
import com.longtoast.bilbil.dto.ProductListDTO

class ProductAdapter(
    private var productList: List<ProductListDTO>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemProductListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductListDTO) {
            binding.textItemTitle.text = product.title
            binding.textItemLocation.text = product.address
            binding.textItemPrice.text = "₩ ${String.format("%,d", product.price)}"

            // ⭐ 이미지 URL 조립 (nullable-safe)
            val raw = product.imageUrl ?: ""
            val fullUrl = when {
                raw.startsWith("/") ->
                    ServerConfig.HTTP_BASE_URL.removeSuffix("/") + raw
                raw.startsWith("http") ->
                    raw
                else -> null
            }

            Glide.with(binding.root)
                .load(fullUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(binding.imageItemThumbnail)

            binding.root.setOnClickListener {
                onItemClick(product.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = productList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(productList[position])
    }

    fun updateList(newList: List<ProductListDTO>) {
        productList = newList
        notifyDataSetChanged()
    }
}

