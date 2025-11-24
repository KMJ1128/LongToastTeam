package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.util.RemoteImageLoader
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String,
    private val partnerNickname: String?,
    private val partnerProfileImageUrl: String?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_DATE = 0
    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    private val currentUserIdInt: Int? = currentUserId.toIntOrNull()

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())
    private val headerFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault())

    // ============================================================
    // 날짜 헤더 + 메시지 구조 (외부 노출 안 함)
    // ============================================================
    internal sealed class ChatItem {
        data class DateHeader(val label: String) : ChatItem()
        data class Message(val data: ChatMessage) : ChatItem()
    }

    private val items = mutableListOf<ChatItem>()

    init {
        rebuildItems()
    }

    fun submitMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        rebuildItems()
        notifyDataSetChanged()
    }

    private fun rebuildItems() {
        items.clear()
        var lastDateLabel: String? = null

        messages.forEach { msg ->
            val dateLabel = formatDateHeader(msg.sentAt)
            if (dateLabel != null && dateLabel != lastDateLabel) {
                items.add(ChatItem.DateHeader(dateLabel))
                lastDateLabel = dateLabel
            }
            items.add(ChatItem.Message(msg))
        }
    }

    // ============================================================
    // ViewHolders
    // ============================================================

    inner class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.text_date_header)
        fun bind(label: String) {
            dateText.text = label
        }
    }

    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)

        fun bind(message: ChatMessage) {
            if (message.imageUrl.isNullOrBlank()) {
                imageAttachment?.visibility = View.GONE
            } else {
                imageAttachment?.visibility = View.VISIBLE
                RemoteImageLoader.load(imageAttachment!!, message.imageUrl, R.drawable.bg_image_placeholder)
            }

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
        }
    }

    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)
        private val profileImage: ImageView? = view.findViewById(R.id.image_profile_received)

        fun bind(message: ChatMessage) {
            if (message.imageUrl.isNullOrBlank()) {
                imageAttachment?.visibility = View.GONE
            } else {
                imageAttachment?.visibility = View.VISIBLE
                RemoteImageLoader.load(imageAttachment!!, message.imageUrl, R.drawable.bg_image_placeholder)
            }

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
            nicknameText.text = partnerNickname ?: "상대방(${message.senderId})"

            profileImage?.let {
                RemoteImageLoader.load(it, partnerProfileImageUrl, R.drawable.no_profile)
            }
        }
    }

    // ============================================================
    // Recycler Adapter Override
    // ============================================================

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is ChatItem.DateHeader -> VIEW_TYPE_DATE
            is ChatItem.Message -> {
                val sender = item.data.senderId
                if (sender == currentUserIdInt) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val view = inflater.inflate(R.layout.item_chat_date_header, parent, false)
                DateViewHolder(view)
            }
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_chat_message_sent, parent, false)
                SentMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_chat_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatItem.DateHeader -> (holder as DateViewHolder).bind(item.label)
            is ChatItem.Message -> {
                when (holder.itemViewType) {
                    VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(item.data)
                    VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(item.data)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ============================================================
    // 날짜/시간 포맷
    // ============================================================

    private fun formatTime(isoTimeString: String?): String {
        return try {
            if (isoTimeString.isNullOrEmpty()) return ""
            val date = serverFormat.parse(isoTimeString) ?: return ""
            displayFormat.format(date)
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatDateHeader(isoTimeString: String?): String? {
        return try {
            if (isoTimeString.isNullOrEmpty()) return null
            val date = serverFormat.parse(isoTimeString) ?: return null
            headerFormat.format(date)
        } catch (e: Exception) {
            null
        }
    }
}
