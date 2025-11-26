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

class MyItemsAdapter(
    private val productList: List<ProductDTO>,
    private val onItemClicked: (ProductDTO) -> Unit,
    private val onReviewClicked: ((ProductDTO) -> Unit)? = null
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

            title.text = product.title

            // 가격
            val priceDisplay = "₩ ${String.format("%,d", product.price)} / 일"
            price.text = priceDisplay

            // 보증금
            if ((product.deposit ?: 0) > 0) {
                depositTxt.visibility = View.VISIBLE
                depositTxt.text = "₩ ${String.format("%,d", product.deposit)} / 보증금"
            } else {
                depositTxt.visibility = View.GONE
            }

            // 주소
            location.text = product.address ?: "위치 미정"

            // 🚨 이미지 URL 처리 (Base64 → URL 방식으로 변경)
            val rawUrl = product.imageUrls?.firstOrNull()
            val finalUrl = when {
                rawUrl.isNullOrEmpty() -> null

                rawUrl.startsWith("/") ->
                    ServerConfig.HTTP_BASE_URL.removeSuffix("/") + rawUrl

                rawUrl.startsWith("http") ->
                    rawUrl

                else -> null
            }

            Glide.with(thumbnail.context)
                .load(finalUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(thumbnail)

            // 상태 표시
            val isAvailable = product.status == "AVAILABLE"
            status.visibility = View.VISIBLE
            status.text = if (isAvailable) "대여 가능" else "대여중"
            status.setBackgroundResource(
                if (isAvailable) R.drawable.badge_available
                else R.drawable.badge_rented
            )

            itemView.setOnClickListener { onItemClicked(product) }

            // 리뷰 버튼
            if (product.transactionId != null) {
                reviewButton.visibility = View.VISIBLE
                reviewButton.setOnClickListener { onReviewClicked?.invoke(product) }
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
