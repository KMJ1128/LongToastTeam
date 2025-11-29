// com.longtoast.bilbil.ReviewListAdapter.kt
package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.dto.ReviewDTO

class ReviewListAdapter(
    private var reviews: List<ReviewDTO>,
    private val reviewType: String   // "WRITTEN" / "RECEIVED" / "SELLER"
) : RecyclerView.Adapter<ReviewListAdapter.ReviewHolder>() {

    inner class ReviewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nickname: TextView = itemView.findViewById(R.id.text_review_nickname)
        val date: TextView = itemView.findViewById(R.id.text_review_date)
        val ratingBar: RatingBar = itemView.findViewById(R.id.rating_review_item)
        val itemName: TextView = itemView.findViewById(R.id.text_review_item_name)
        val content: TextView = itemView.findViewById(R.id.text_review_content)

        fun bind(review: ReviewDTO) {
            nickname.text = review.reviewerNickname ?: "익명"
            date.text = review.createdAt ?: ""
            ratingBar.rating = review.rating.toFloat()
            content.text = review.comment ?: ""

            // 🔥 세 모드 전부 같은 양식: 정보가 있으면 표시
            val title = review.itemTitle.orEmpty()
            val seller = review.sellerNickname.orEmpty()
            val period = review.rentalPeriod.orEmpty()

            val infoParts = listOf(
                title,
                if (seller.isNotBlank()) "판매자: $seller" else "",
                if (period.isNotBlank()) "대여기간: $period" else ""
            ).filter { it.isNotBlank() }

            if (infoParts.isNotEmpty()) {
                itemName.visibility = View.VISIBLE
                itemName.text = infoParts.joinToString(" | ")
            } else {
                itemName.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewHolder, position: Int) {
        holder.bind(reviews[position])
    }

    override fun getItemCount(): Int = reviews.size

    fun updateList(newList: List<ReviewDTO>) {
        reviews = newList
        notifyDataSetChanged()
    }
}
