package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.dto.ProductDTO
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log

class MyItemsAdapter(
    private val productList: List<ProductDTO>,
    private val onItemClicked: (ProductDTO) -> Unit
) : RecyclerView.Adapter<MyItemsAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_item_title)
        val location: TextView = view.findViewById(R.id.text_item_location)
        val price: TextView = view.findViewById(R.id.text_item_price)
        val status: TextView = view.findViewById(R.id.text_item_status)
        val thumbnail: ImageView = view.findViewById(R.id.image_item_thumbnail)

        fun bind(product: ProductDTO) {

            // 1. 제목 및 가격/보증금 표시
            title.text = product.title

            val priceDisplay = "₩ ${String.format("%,d", product.price ?: 0)}"

            // 가격 단위는 현재 description에 임시로 포함되어 있으므로, 이를 파싱하거나 추정합니다.
            val unit = if (product.description?.contains("(가격 단위:") == true) {
                product.description.substringAfter("(가격 단위:").substringBefore(")")
            } else {
                "일" // 파싱 실패 시 기본값
            }

            price.text = "$priceDisplay / $unit"

            // 🚨 [핵심 수정] 위치 정보 표시 로직
            val depositDisplay = if ((product.deposit ?: 0) > 0) {
                " (보증금 ₩ ${String.format("%,d", product.deposit)})"
            } else {
                ""
            }

            // 💡 [수정] product.address를 최우선으로 표시
            val addressDisplay = if (product.address.isNullOrEmpty()) {
                "위치 미정"
            } else {
                product.address
            }

            location.text = "$addressDisplay$depositDisplay"


            // 2. Base64 디코딩 및 이미지 표시
            val firstBase64Image = product.imageUrls?.firstOrNull()

            if (firstBase64Image != null && firstBase64Image.isNotEmpty()) {

                val cleanBase64 = if (firstBase64Image.startsWith("data:")) {
                    firstBase64Image.substringAfterLast("base64,")
                } else {
                    firstBase64Image
                }

                var decodedBitmap: android.graphics.Bitmap? = null

                try {
                    // NO_WRAP 디코딩 시도
                    var imageBytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
                    decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    if (decodedBitmap == null) {
                        // DEFAULT 플래그로 재시도
                        Log.w("Base64Decode", "NO_WRAP 디코딩 실패, DEFAULT 재시도")
                        imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    }

                    if (decodedBitmap != null) {
                        thumbnail.setImageBitmap(decodedBitmap)
                    } else {
                        throw IllegalArgumentException("Bitmap 디코딩 실패")
                    }

                } catch (e: IllegalArgumentException) {
                    Log.e("Base64Decode", "❌ Base64 문자열 형식 오류: ${e.message}")
                    thumbnail.setImageResource(R.drawable.ic_default_category)
                } catch (e: Exception) {
                    Log.e("Base64Decode", "❌ 기타 디코딩 오류", e)
                    thumbnail.setImageResource(R.drawable.ic_default_category)
                }
            } else {
                thumbnail.setImageResource(R.drawable.ic_default_category)
            }


            // 3. 상태 표시 (대여중 / 대여 가능)
            val isAvailable = product.status == "AVAILABLE"

            status.visibility = View.VISIBLE
            status.text = if (isAvailable) "대여 가능" else "대여중"
            // 💡 R.drawable.badge_background_available 리소스가 존재한다고 가정합니다.
            status.setBackgroundResource(
                if (isAvailable) R.drawable.ic_launcher_background
                else R.drawable.badge_background
            )

            itemView.setOnClickListener { onItemClicked(product) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_list, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(productList[position])
    }

    override fun getItemCount(): Int = productList.size
}