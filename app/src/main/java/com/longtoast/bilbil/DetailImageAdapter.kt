package com.longtoast.bilbil.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R
import com.longtoast.bilbil.util.RemoteImageLoader

class DetailImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<DetailImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_detail_slider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        // item_detail_image.xml 레이아웃을 inflate 합니다 (아래 XML 코드 참고)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        RemoteImageLoader.load(holder.imageView, imageUrls[position], R.drawable.bg_image_placeholder)
    }

    override fun getItemCount(): Int = imageUrls.size
}