package com.longtoast.bilbil

import android.util.Log
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

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: Int,                     // ← 최종: Int 로 통일
    private val partnerNickname: String?,
    private val partnerProfileImageUrl: String?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_DATE = 0
    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    private val serverFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    )
    private val displayFormat =
        SimpleDateFormat("a h:mm", Locale.getDefault())
    private val headerFormat =
        SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault())

    // 날짜 + 메시지 구조
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
        messages.addAll(
            newMessages
                .filter { !it.content.isNullOrBlank() || !it.imageUrl.isNullOrBlank() }
                .sortedWith(compareBy({ parseDate(it.sentAt) ?: Date(0) }, { it.id ?: Long.MIN_VALUE }))
        )
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

    // 날짜 ViewHolder
    inner class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.text_date_header)
        fun bind(label: String) {
            dateText.text = label
        }
    }

    // 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)

        fun bind(msg: ChatMessage) {
            // 이미지
            if (msg.imageUrl.isNullOrBlank()) {
                imageAttachment?.visibility = View.GONE
            } else {
                imageAttachment?.visibility = View.VISIBLE
                RemoteImageLoader.load(imageAttachment!!, msg.imageUrl, R.drawable.bg_image_placeholder)
            }

            // 텍스트
            if (!msg.content.isNullOrEmpty()) {
                messageText.visibility = View.VISIBLE
                messageText.text = msg.content
            } else {
                messageText.visibility = View.GONE
            }

            timestampText.text = formatTime(msg.sentAt)
        }
    }

    // 받은 메시지 ViewHolder
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)
        private val profileImage: ImageView? = view.findViewById(R.id.image_profile_received)

        fun bind(msg: ChatMessage) {

            // 이미지
            if (msg.imageUrl.isNullOrBlank()) {
                imageAttachment?.visibility = View.GONE
            } else {
                imageAttachment?.visibility = View.VISIBLE
                RemoteImageLoader.load(imageAttachment!!, msg.imageUrl, R.drawable.bg_image_placeholder)
            }

            // 텍스트
            if (!msg.content.isNullOrEmpty()) {
                messageText.visibility = View.VISIBLE
                messageText.text = msg.content
            } else {
                messageText.visibility = View.GONE
            }

            nicknameText.text = partnerNickname ?: "상대방(${msg.senderId})"
            timestampText.text = formatTime(msg.sentAt)

            profileImage?.let {
                RemoteImageLoader.load(it, partnerProfileImageUrl, R.drawable.no_profile)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is ChatItem.DateHeader -> VIEW_TYPE_DATE
            is ChatItem.Message ->
                if (item.data.senderId == currentUserId) VIEW_TYPE_SENT
                else VIEW_TYPE_RECEIVED
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE ->
                DateViewHolder(inflater.inflate(R.layout.item_chat_date_header, parent, false))

            VIEW_TYPE_SENT ->
                SentMessageViewHolder(inflater.inflate(R.layout.item_chat_message_sent, parent, false))

            else ->
                ReceivedMessageViewHolder(
                    inflater.inflate(R.layout.item_chat_message_received, parent, false)
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {

            is ChatItem.DateHeader ->
                (holder as DateViewHolder).bind(item.label)

            is ChatItem.Message ->
                if (holder.itemViewType == VIEW_TYPE_SENT)
                    (holder as SentMessageViewHolder).bind(item.data)
                else
                    (holder as ReceivedMessageViewHolder).bind(item.data)
        }
    }

    // 시간 포맷
    private fun formatTime(raw: String?): String {
        return parseDate(raw)?.let { displayFormat.format(it) } ?: ""
    }

    // 날짜 헤더 포맷
    private fun formatDateHeader(raw: String?): String? {
        return parseDate(raw)?.let { headerFormat.format(it) }
    }

    private fun parseDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null

        val cleaned = raw.replace("/", "-").substringBeforeLast(".")

        serverFormats.forEach { format ->
            try {
                return format.parse(cleaned)
            } catch (_: Exception) {
            }
        }

        Log.w("ChatAdapter", "날짜 파싱 실패: $raw")
        return null
    }
}
