package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.databinding.ItemProductBinding
// 💡 Product 모델 import가 필요할 수 있습니다.

class ProductAdapter(
    // 💡 1. 첫 번째 매개변수
    private var items: List<Product>,
    // 💡 2. 두 번째 매개변수: 쉼표로 명확하게 구분
    private val onItemClicked: (itemId: Int) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    class VH(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.titleText.text = item.title
        holder.binding.descText.text = item.description ?: ""
        holder.binding.priceText.text = item.price?.let { "₩ ${it}" } ?: ""

        val imageUrl = item.imageUrls?.firstOrNull()
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(holder.binding.productImage.context)
                .load(imageUrl)
                .centerCrop()
                .into(holder.binding.productImage)
        } else {
            holder.binding.productImage.setImageResource(android.R.drawable.ic_menu_report_image)
        }

        // 💡 수정: 클릭 이벤트 발생 시 아이템의 ID를 콜백 함수로 전달
        holder.binding.root.setOnClickListener {
            onItemClicked(item.id)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<Product>) {
        items = newList
        notifyDataSetChanged()
    }
}