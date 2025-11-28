package com.longtoast.bilbil

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.longtoast.bilbil.dto.ProductDTO

class MyItemsAdapter(
    private val productList: List<ProductDTO>,
    private val isOwner: Boolean,
    private val onItemClicked: (ProductDTO) -> Unit,
    private val onReviewClicked: ((ProductDTO) -> Unit)? = null,
    private val onEditClicked: ((ProductDTO) -> Unit)? = null,
    private val onDeleteClicked: ((ProductDTO) -> Unit)? = null
) : RecyclerView.Adapter<MyItemsAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_item_title)
        val location: TextView = view.findViewById(R.id.text_item_location)
        val price: TextView = view.findViewById(R.id.text_item_price)
        val depositTxt: TextView = view.findViewById(R.id.text_item_deposit)
        val status: TextView = view.findViewById(R.id.text_item_status)
        val thumbnail: ImageView = view.findViewById(R.id.image_item_thumbnail)

        // 버튼들
        val reviewButton: Button = view.findViewById(R.id.btn_write_review)
        val editButton: MaterialButton = view.findViewById(R.id.btn_edit_item)
        val deleteButton: MaterialButton = view.findViewById(R.id.btn_delete_item)
        val actionContainer: View = view.findViewById(R.id.layout_item_actions)

        fun bind(product: ProductDTO) {
            // 1. 기본 정보 바인딩
            title.text = product.title
            location.text = product.address ?: "위치 미정"

            val unitLabel = PriceUnitMapper.toLabel(product.price_unit)
            price.text = "₩ ${String.format("%,d", product.price)} / $unitLabel"

            if ((product.deposit ?: 0) > 0) {
                depositTxt.visibility = View.VISIBLE
                depositTxt.text = "보증금 ₩ ${String.format("%,d", product.deposit)}"
            } else {
                depositTxt.visibility = View.GONE
            }

            // 이미지 로드
            val rawUrl = product.imageUrls?.firstOrNull()
            val finalUrl = ImageUrlUtils.resolve(rawUrl)
            Glide.with(thumbnail.context)
                .load(finalUrl)
                .placeholder(R.drawable.ic_default_category)
                .into(thumbnail)

            // 2. 상태 표시 (사라지는 문제 해결을 위해 항상 VISIBLE 설정)
            status.visibility = View.VISIBLE
            val isAvailable = product.status == "AVAILABLE"
            status.text = if (isAvailable) "대여 가능" else "대여중"
            status.setBackgroundResource(
                if (isAvailable) R.drawable.badge_available else R.drawable.badge_rented
            )

            // 아이템 클릭
            itemView.setOnClickListener { onItemClicked(product) }

            // 3. 버튼 표시 로직
            if (isOwner) {
                // 내가 등록한 물품 (판매자)
                actionContainer.visibility = View.VISIBLE
                reviewButton.visibility = View.GONE

                editButton.setOnClickListener { onEditClicked?.invoke(product) }
                deleteButton.setOnClickListener { onDeleteClicked?.invoke(product) }
            } else {
                // 내가 빌린 물품 (구매자)
                actionContainer.visibility = View.GONE

                // 로그로 transactionId 확인 (디버깅용)
                Log.d("MyItemsAdapter", "Item: ${product.title}, TransactionId: ${product.transactionId}")

                if (product.transactionId != null && product.transactionId != 0L) {
                    reviewButton.visibility = View.VISIBLE
                    reviewButton.setOnClickListener { onReviewClicked?.invoke(product) }
                } else {
                    // 거래 ID가 없으면 버튼 숨김 (아직 거래 확정 전일 수도 있음)
                    reviewButton.visibility = View.GONE
                }
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