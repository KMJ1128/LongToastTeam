package com.longtoast.bilbil

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImagePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imageView = findViewById<ImageView>(R.id.image_preview)
        val rawUrl = intent.getStringExtra("IMAGE_URL")

        val fullUrl = ImageUrlUtils.resolve(rawUrl)

        if (!fullUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(fullUrl)
                .placeholder(R.drawable.no_profile)
                .error(R.drawable.no_profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.no_profile)
        }

        // 화면 아무데나 탭하면 닫기 (원하면 제거해도 됨)
        imageView.setOnClickListener { finish() }
    }
}
