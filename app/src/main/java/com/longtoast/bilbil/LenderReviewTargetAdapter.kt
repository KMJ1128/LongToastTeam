// com.longtoast.bilbil.LenderReviewTargetAdapter.kt
package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.databinding.ItemLenderReviewTargetBinding
import com.longtoast.bilbil.dto.LenderReviewTargetDTO

class LenderReviewTargetAdapter(
    private var items: List<LenderReviewTargetDTO>,
    private val onWriteClick: (LenderReviewTargetDTO) -> Unit
) : RecyclerView.Adapter<LenderReviewTargetAdapter.TargetViewHolder>() {

    inner class TargetViewHolder(
        private val binding: ItemLenderReviewTargetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LenderReviewTargetDTO) {
            binding.textBorrowerNickname.text = item.borrowerNickname ?: "알 수 없는 사용자"
            binding.textItemTitle.text = item.itemTitle ?: "제목 없음"
            binding.textRentalPeriod.text = item.rentalPeriod ?: ""

            // 카드 전체 클릭 / 버튼 클릭 둘 다 리뷰 작성으로 연결
            binding.root.setOnClickListener { onWriteClick(item) }
            binding.btnWriteReview.setOnClickListener { onWriteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TargetViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemLenderReviewTargetBinding.inflate(inflater, parent, false)
        return TargetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TargetViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<LenderReviewTargetDTO>) {
        items = newItems
        notifyDataSetChanged()
    }
}
