package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.longtoast.bilbil.R
import com.longtoast.bilbil.databinding.ItemProductListBinding
import com.longtoast.bilbil.dto.ProductListDTO

class ProductAdapter(
    private var productList: List<ProductListDTO>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    companion object {
        private const val BASE_URL = ServerConfig.IMAGE_BASE_URL
    }

    inner class ViewHolder(val binding: ItemProductListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductListDTO) {
            binding.textItemTitle.text = product.title
            binding.textItemLocation.text = product.address
            binding.textItemPrice.text = "₩ ${String.format("%,d", product.price)}"

            val url = product.mainImageUrl

            if (url.isNullOrBlank()) {
                binding.imageItemThumbnail.setImageResource(R.drawable.ic_default_category)
            } else {
                // "/uploads/..." → 절대 URL로 변환
                val fullUrl = if (url.startsWith("/")) {
                    BASE_URL + url
                } else {
                    url
                }

                Glide.with(binding.root)
                    .load(fullUrl)
                    .apply(
                        RequestOptions()
                            .placeholder(R.drawable.ic_default_category)
                            .error(R.drawable.ic_default_category)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    )
                    .into(binding.imageItemThumbnail)
            }

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
