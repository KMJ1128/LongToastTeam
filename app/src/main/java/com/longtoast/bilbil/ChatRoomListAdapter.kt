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
        private const val BASE_URL = ServerConfig.IMAGE_BASE_URL
    }

    inner class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val partnerName: TextView = view.findViewById(R.id.text_nickname)
        private val lastMessage: TextView = view.findViewById(R.id.text_last_message)
        private val thumbnail: ImageView = view.findViewById(R.id.image_profile)
        private val timeText: TextView = view.findViewById(R.id.text_time)
        private val unreadBadge: TextView = view.findViewById(R.id.text_unread_badge)

        fun bind(room: ChatRoomListDTO) {
            // 닉네임
            partnerName.text = room.partnerNickname ?: "알 수 없음"

            // 최근 메시지 내용
            val lastContent = room.lastMessageContent
            val itemImage = room.itemMainImageUrl

            lastMessage.text = when {
                !lastContent.isNullOrEmpty() -> lastContent
                !itemImage.isNullOrEmpty() -> "[사진]"
                else -> "(최근 메시지 없음)"
            }

            // -------------------------------
            // 🔥 프로필 / 아이템 이미지 URL 처리
            // -------------------------------
            val primaryImage = room.partnerProfileImageUrl ?: itemImage

            if (primaryImage.isNullOrBlank()) {
                thumbnail.setImageResource(R.drawable.no_profile)
            } else {
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

            // -------------------------------
            // 읽지 않은 메시지 뱃지
            // -------------------------------
            val unread = room.unreadCount ?: 0
            if (unread > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = unread.toString()
            } else {
                unreadBadge.visibility = View.GONE
            }

            // -------------------------------
            // 시간 표시
            // -------------------------------
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
