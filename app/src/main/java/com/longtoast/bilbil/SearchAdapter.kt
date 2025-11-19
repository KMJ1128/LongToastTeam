package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchAdapter(private var list: List<SearchItem>, private val onClick: (SearchItem) -> Unit) :
    RecyclerView.Adapter<SearchAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        private val text: TextView = v.findViewById(R.id.tvSearchName)
        fun bind(item: SearchItem) {
            text.text = item.name
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: Holder, pos: Int) = holder.bind(list[pos])

    fun updateList(newList: List<SearchItem>) {
        list = newList
        notifyDataSetChanged()
    }
}
