package com.longtoast.bilbil.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.ImageUrlUtils
import com.longtoast.bilbil.R

class DetailImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<DetailImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_detail_slider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val raw = imageUrls[position]

        // 1) 먼저 URL/상대경로 → 절대 URL 로 시도
        val resolved = ImageUrlUtils.resolve(raw)

        if (resolved != null &&
            (resolved.startsWith("http://") || resolved.startsWith("https://"))
        ) {
            Glide.with(holder.itemView.context)
                .load(resolved)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .into(holder.imageView)
            return
        }

        // 2) URL이 아닌 경우 → Base64 이미지라고 가정
        try {
            val base64Source = raw

            val cleanBase64 =
                if (base64Source.startsWith("data:")) {
                    base64Source.substringAfterLast("base64,")
                } else {
                    base64Source
                }

            val imageBytes = try {
                Base64.decode(cleanBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                Base64.decode(cleanBase64, Base64.DEFAULT)
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap)
            } else {
                holder.imageView.setImageResource(R.drawable.bg_image_placeholder)
            }

        } catch (e: Exception) {
            Log.e("DetailImageAdapter", "Base64 이미지 변환 실패", e)
            holder.imageView.setImageResource(R.drawable.bg_image_placeholder)
        }
    }

    override fun getItemCount(): Int = imageUrls.size
}
