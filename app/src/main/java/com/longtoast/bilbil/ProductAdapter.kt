package com.longtoast.bilbil

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
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

    inner class ViewHolder(val binding: ItemProductListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: ProductListDTO) {
            binding.textItemTitle.text = product.title
            binding.textItemLocation.text = product.address
            binding.textItemPrice.text = "₩ ${String.format("%,d", product.price)}"

            val mainImageUrl = product.mainImageUrl

            when {
                mainImageUrl.isNullOrBlank() -> {
                    binding.imageItemThumbnail.setImageResource(R.drawable.ic_default_category)
                }

                mainImageUrl.startsWith("http", ignoreCase = true) -> {
                    Glide.with(binding.root)
                        .load(mainImageUrl)
                        .apply(
                            RequestOptions()
                                .placeholder(R.drawable.ic_default_category)
                                .error(R.drawable.ic_default_category)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        )
                        .into(binding.imageItemThumbnail)
                }

                else -> {
                    try {
                        val cleanBase64 = mainImageUrl.substringAfterLast("base64,", mainImageUrl)
                        val imageBytes = try {
                            Base64.decode(cleanBase64, Base64.NO_WRAP)
                        } catch (_: IllegalArgumentException) {
                            Base64.decode(cleanBase64, Base64.DEFAULT)
                        }

                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            Glide.with(binding.root)
                                .load(bitmap)
                                .apply(
                                    RequestOptions()
                                        .placeholder(R.drawable.ic_default_category)
                                        .error(R.drawable.ic_default_category)
                                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                                )
                                .into(binding.imageItemThumbnail)
                        } else {
                            binding.imageItemThumbnail.setImageResource(R.drawable.ic_default_category)
                        }
                    } catch (_: Exception) {
                        binding.imageItemThumbnail.setImageResource(R.drawable.ic_default_category)
                    }
                }
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
