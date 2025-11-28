package com.longtoast.bilbil.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R

class CategoryAdapter(
    private val categories: List<String>,
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.category_icon)
        val textName: TextView = view.findViewById(R.id.category_name)

        fun bind(name: String) {
            textName.text = name

            // 🔥 카테고리별 아이콘 매핑
            val iconRes = when (name) {
                "자전거" -> R.drawable.ic_bike
                "가구" -> R.drawable.ic_furniture
                "캠핑" -> R.drawable.ic_camping
                "전자제품" -> R.drawable.ic_digital
                "운동" -> R.drawable.ic_kkk
                "의류" -> R.drawable.ic_dwd
                else -> R.drawable.ic_trash
            }

            icon.setImageResource(iconRes)

            itemView.setOnClickListener {
                onCategoryClick(name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_grid, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size
}
