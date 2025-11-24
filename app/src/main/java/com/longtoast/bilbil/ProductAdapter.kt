package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R
import com.longtoast.bilbil.databinding.ItemProductListBinding
import com.longtoast.bilbil.dto.ProductListDTO
import com.longtoast.bilbil.util.RemoteImageLoader

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

            RemoteImageLoader.load(binding.imageItemThumbnail, product.mainImageUrl, R.drawable.ic_default_category)

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
