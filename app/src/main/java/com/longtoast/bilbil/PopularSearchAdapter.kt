package com.longtoast.bilbil.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R

class PopularSearchAdapter(
    private var items: List<String>,
    private val onKeywordClick: (String) -> Unit
) : RecyclerView.Adapter<PopularSearchAdapter.PopularViewHolder>() {

    inner class PopularViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textKeyword: TextView = itemView.findViewById(R.id.text_keyword)

        fun bind(keyword: String) {
            textKeyword.text = keyword

            itemView.setOnClickListener {
                onKeywordClick(keyword)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PopularViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_popular_keyword, parent, false)
        return PopularViewHolder(view)
    }

    override fun onBindViewHolder(holder: PopularViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged()
    }
}
