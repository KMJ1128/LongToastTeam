package com.longtoast.bilbil.adapter

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R

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
        val base64String = imageUrls[position]

        try {
            // Base64 문자열 정리 (Prefix 제거)
            val cleanBase64 = if (base64String.startsWith("data:")) {
                base64String.substringAfterLast("base64,")
            } else {
                base64String
            }

            // 디코딩 (NO_WRAP 시도 후 실패시 DEFAULT 재시도 - MyItemsAdapter 로직 참고)
            val imageBytes = try {
                Base64.decode(cleanBase64, Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                Base64.decode(cleanBase64, Base64.DEFAULT)
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap)
            } else {
                holder.imageView.setImageResource(R.drawable.bg_image_placeholder)
            }
        } catch (e: Exception) {
            Log.e("DetailImageAdapter", "이미지 디코딩 실패", e)
            holder.imageView.setImageResource(R.drawable.bg_image_placeholder)
        }
    }

    override fun getItemCount(): Int = imageUrls.size
}