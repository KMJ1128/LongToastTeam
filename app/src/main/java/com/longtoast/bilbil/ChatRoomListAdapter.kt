package com.longtoast.bilbil

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.longtoast.bilbil.R
import com.longtoast.bilbil.dto.ChatRoomListDTO
import java.text.SimpleDateFormat
import java.util.*

class ChatRoomListAdapter(
    private val roomList: List<ChatRoomListDTO>,
    private val onItemClicked: (ChatRoomListDTO) -> Unit
) : RecyclerView.Adapter<ChatRoomListAdapter.RoomViewHolder>() {

    companion object {
        private const val BASE_URL = ServerConfig.IMAGE_BASE_URL   // <-- 민재 서버 주소로 교체
    }

    inner class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val partnerName: TextView = view.findViewById(R.id.text_nickname)
        val lastMessage: TextView = view.findViewById(R.id.text_last_message)
        val thumbnail: ImageView = view.findViewById(R.id.image_profile)
        val timeText: TextView = view.findViewById(R.id.text_time)

        fun bind(room: ChatRoomListDTO) {
            partnerName.text = room.partnerNickname ?: "알 수 없음"

            val lastContent = room.lastMessageContent
            val itemImage = room.itemMainImageUrl
            lastMessage.text = when {
                !lastContent.isNullOrEmpty() -> lastContent
                !itemImage.isNullOrEmpty() -> "[사진]"
                else -> "(최근 메시지 없음)"
            }

            // -------------------------------
            // 🔥 이미지 URL 처리 (Multipart 버전)
            // -------------------------------

            val primaryImage = room.partnerProfileImageUrl ?: itemImage

            if (primaryImage.isNullOrBlank()) {
                thumbnail.setImageResource(R.drawable.no_profile)
            } else {
                // DB에는 "/uploads/..." 이런 값이 들어있음 → 절대 URL로 변환
                val fullUrl = if (primaryImage.startsWith("/")) {
                    BASE_URL + primaryImage
                } else {
                    primaryImage
                }

                Glide.with(itemView.context)
                    .load(fullUrl)
                    .apply(
                        RequestOptions()
                            .placeholder(R.drawable.no_profile)
                            .error(R.drawable.no_profile)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    )
                    .into(thumbnail)
            }

            // 시간 처리
            timeText.text = room.lastMessageTime?.let {
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = parser.parse(it)
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    formatter.format(date!!)
                } catch (e: Exception) {
                    Log.e("TIME_PARSE_ERROR", "시간 변환 실패: ${e.message}")
                    ""
                }
            } ?: ""

            itemView.setOnClickListener { onItemClicked(room) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_room_list, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(roomList[position])
    }

    override fun getItemCount(): Int = roomList.size
}
