// java/com/longtoast/bilbil/MyItemsAdapter.kt
package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.dto.ProductListDTO

class MyItemsAdapter(
    private val productList: List<ProductListDTO>,
    private val onItemClicked: (ProductListDTO) -> Unit
) : RecyclerView.Adapter<MyItemsAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_item_title)
        val location: TextView = view.findViewById(R.id.text_item_location)
        val price: TextView = view.findViewById(R.id.text_item_price)
        val status: TextView = view.findViewById(R.id.text_item_status)
        val thumbnail: ImageView = view.findViewById(R.id.image_item_thumbnail)

        fun bind(product: ProductListDTO) {
            title.text = product.title
            location.text = product.address
            price.text = "₩ ${String.format("%,d", product.price)}" // 가격 포맷은 임시

            // 이미지 로드 (Glide 필요)
            Glide.with(itemView.context)
                .load(product.mainImageUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(thumbnail)

            // 상태 표시
            if (product.status == "UNAVAILABLE") {
                status.visibility = View.VISIBLE
                status.text = "대여중"
                status.setBackgroundResource(R.drawable.badge_background) // 빨간색 배지
            } else {
                status.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClicked(product) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_list, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(productList[position])
    }

    override fun getItemCount(): Int = productList.size
}