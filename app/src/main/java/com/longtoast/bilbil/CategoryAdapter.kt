//package com.longtoast.bilbil
//
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.TextView
//import androidx.recyclerview.widget.RecyclerView
//
//class CategoryAdapter(
//    private val items: List<String>,
//    private val onClick: (String) -> Unit
//) : RecyclerView.Adapter<CategoryAdapter.VH>() {
//    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val title: TextView = itemView.findViewById(android.R.id.text1)
//        init {
//            itemView.setOnClickListener {
//                val pos = bindingAdapterPosition
//                if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
//            }
//        }
//    }
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
//        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
//        return VH(v)
//    }
//    override fun onBindViewHolder(holder: VH, position: Int) {
//        holder.title.text = items[position]
//    }
//    override fun getItemCount(): Int = items.size
//}
