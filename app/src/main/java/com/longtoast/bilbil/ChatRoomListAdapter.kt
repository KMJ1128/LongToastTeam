package com.longtoast.bilbil

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.dto.ChatRoomListDTO

class ChatRoomListAdapter(
    private val roomList: List<ChatRoomListDTO>,
    private val onItemClicked: (ChatRoomListDTO) -> Unit
) : RecyclerView.Adapter<ChatRoomListAdapter.RoomViewHolder>() {

    inner class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val partnerName: TextView = view.findViewById(R.id.text_nickname)
        val lastMessage: TextView = view.findViewById(R.id.text_last_message)
        val thumbnail: ImageView = view.findViewById(R.id.image_profile)
        val timeText: TextView = view.findViewById(R.id.text_time)
        val unreadBadge: TextView = view.findViewById(R.id.text_unread_badge)

        fun bind(room: ChatRoomListDTO) {

            partnerName.text = room.partnerNickname ?: "알 수 없음"

            val lastContent = room.lastMessageContent
            lastMessage.text = lastContent ?: "(최근 메시지 없음)"

            val resolved = ImageUrlUtils.resolve(room.partnerProfileImageUrl)
            if (!resolved.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(resolved)
                    .placeholder(R.drawable.no_profile)
                    .error(R.drawable.no_profile)
                    .circleCrop()
                    .into(thumbnail)
            } else {
                thumbnail.setImageResource(R.drawable.no_profile)
            }

            // --- 마지막 메시지 시간 ---
            timeText.text = room.lastMessageTime?.let {
                try {
                    val parser = java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss",
                        java.util.Locale.getDefault()
                    )
                    val date = parser.parse(it)
                    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    formatter.format(date!!)
                } catch (e: Exception) {
                    Log.e("TIME_PARSE_ERROR", "시간 변환 실패: ${e.message}")
                    ""
                }
            } ?: ""

            // --- 🔥 새로운 unreadCount 표시 추가된 부분 ---
            if ((room.unreadCount ?: 0) > 0) {
                unreadBadge.text = room.unreadCount.toString()
                unreadBadge.visibility = View.VISIBLE
            } else {
                unreadBadge.visibility = View.GONE
            }

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
