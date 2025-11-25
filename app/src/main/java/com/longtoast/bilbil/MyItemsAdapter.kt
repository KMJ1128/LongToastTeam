package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.dto.ProductDTO
import android.widget.Button
import com.bumptech.glide.Glide
import com.longtoast.bilbil.R

class MyItemsAdapter(
    private val productList: List<ProductDTO>,
    private val onItemClicked: (ProductDTO) -> Unit,
    private val onReviewClicked: ((ProductDTO) -> Unit)? = null   // ✅ 추가
) : RecyclerView.Adapter<MyItemsAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_item_title)
        val location: TextView = view.findViewById(R.id.text_item_location)
        val price: TextView = view.findViewById(R.id.text_item_price)
        val depositTxt: TextView = view.findViewById(R.id.text_item_deposit)
        val status: TextView = view.findViewById(R.id.text_item_status)
        val thumbnail: ImageView = view.findViewById(R.id.image_item_thumbnail)
        val reviewButton: Button = view.findViewById(R.id.btn_write_review)

        fun bind(product: ProductDTO) {

            // 제목
            title.text = product.title

            // 가격 표시
            val priceDisplay = "₩ ${String.format("%,d", product.price ?: 0)}"
            val unit = if (product.description?.contains("(가격 단위:") == true) {
                product.description.substringAfter("(가격 단위:").substringBefore(")")
            } else "일"

            price.text = "$priceDisplay / $unit"

            // 보증금 (주소에서 제거, 따로 표시)
            if ((product.deposit ?: 0) > 0) {
                depositTxt.visibility = View.VISIBLE
                depositTxt.text = "₩ ${String.format("%,d", product.deposit)} / 보증금"
            } else {
                depositTxt.visibility = View.GONE
            }

            // 주소만 표시
            location.text = product.address ?: "위치 미정"

            val firstImageUrl = product.imageUrls?.firstOrNull()
            if (!firstImageUrl.isNullOrEmpty()) {
                Glide.with(thumbnail.context)
                    .load(firstImageUrl)
                    .placeholder(R.drawable.ic_default_category)
                    .error(R.drawable.ic_default_category)
                    .into(thumbnail)
            } else {
                thumbnail.setImageResource(R.drawable.ic_default_category)
            }

            // 상태 표시
            val isAvailable = product.status == "AVAILABLE"
            status.visibility = View.VISIBLE
            status.text = if (isAvailable) "대여 가능" else "대여중"
            status.setBackgroundResource(
                if (isAvailable) R.drawable.badge_available
                else R.drawable.badge_rented
            )

            itemView.setOnClickListener { onItemClicked(product) }

            if (product.transactionId != null) {
                reviewButton.visibility = View.VISIBLE
                reviewButton.setOnClickListener {
                    onReviewClicked?.invoke(product)
                }
            } else {
                reviewButton.visibility = View.GONE
                reviewButton.setOnClickListener(null)
            }
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
