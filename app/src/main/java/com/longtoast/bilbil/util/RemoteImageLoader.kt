package com.longtoast.bilbil.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.longtoast.bilbil.R
import com.longtoast.bilbil.ServerConfig

object RemoteImageLoader {

    private fun resolveUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null

        return if (path.startsWith("http", ignoreCase = true)) {
            path
        } else {
            val base = ServerConfig.HTTP_BASE_URL.trimEnd('/')
            val normalizedPath = path.trimStart('/')
            "$base/$normalizedPath"
        }
    }

    private fun decodeBase64ToBitmap(source: String?): Bitmap? {
        if (source.isNullOrBlank()) return null

        val cleanBase64 = source.substringAfterLast("base64,", source)
        val bytes = try {
            Base64.decode(cleanBase64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            Base64.decode(cleanBase64, Base64.DEFAULT)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun load(imageView: ImageView, source: String?, placeholderRes: Int = R.drawable.ic_default_category) {
        val resolvedUrl = resolveUrl(source)

        when {
            resolvedUrl != null -> {
                Glide.with(imageView.context)
                    .load(resolvedUrl)
                    .apply(
                        RequestOptions()
                            .placeholder(placeholderRes)
                            .error(placeholderRes)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    )
                    .into(imageView)
            }

            else -> {
                val bitmap = decodeBase64ToBitmap(source)
                if (bitmap != null) {
                    Glide.with(imageView.context)
                        .load(bitmap)
                        .apply(
                            RequestOptions()
                                .placeholder(placeholderRes)
                                .error(placeholderRes)
                                .diskCacheStrategy(DiskCacheStrategy.DATA)
                        )
                        .into(imageView)
                } else {
                    imageView.setImageResource(placeholderRes)
                }
            }
        }
    }
}
