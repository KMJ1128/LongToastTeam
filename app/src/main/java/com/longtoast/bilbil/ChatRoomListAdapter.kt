package com.longtoast.bilbil

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.R
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.longtoast.bilbil.util.RemoteImageLoader
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

            // ① 닉네임
            partnerName.text = room.partnerNickname ?: "알 수 없음"

            // ② 최근 메시지
            val lastContent = room.lastMessageContent
            val itemImage = room.itemMainImageUrl

            lastMessage.text = when {
                !lastContent.isNullOrEmpty() -> lastContent
                !itemImage.isNullOrEmpty() -> "[사진]"
                else -> "(최근 메시지 없음)"
            }

            // ③ 이미지 (프로필 > 상품순)
            val primaryImage = room.partnerProfileImageUrl ?: itemImage

            if (primaryImage.isNullOrBlank()) {
                thumbnail.setImageResource(R.drawable.no_profile)
            } else {
                val fullUrl = if (primaryImage.startsWith("/"))
                    BASE_URL + primaryImage
                else
                    primaryImage

                RemoteImageLoader.load(thumbnail, fullUrl, R.drawable.no_profile)
            }

            // ④ 안 읽은 메시지 개수
            val unread = room.unreadCount ?: 0
            if (unread > 0) {
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = unread.toString()
            } else {
                unreadBadge.visibility = View.GONE
            }

            // ⑤ 시간 표시
            timeText.text = room.lastMessageTime?.let {
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val date = parser.parse(it.substringBefore(".")) // 나노초 제거
                    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                    formatter.format(date!!)
                } catch (e: Exception) {
                    Log.e("TIME_PARSE_ERROR", "시간 변환 실패: ${e.message}")
                    ""
                }
            } ?: ""

            // ⑥ 클릭 이벤트
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
