package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.dto.ChatMessage
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val currentUserId: String // 현재 로그인한 사용자의 ID
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    // 1. 뷰 홀더 클래스 정의 (보낸 메시지용)
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            timestampText.text = formatTimestamp(message.sentAt)
        }
    }

    // 2. 뷰 홀더 클래스 정의 (받은 메시지용)
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            timestampText.text = formatTimestamp(message.sentAt)
            // 닉네임은 ChatMessage에 없으므로, 임시로 senderId 사용
            nicknameText.text = message.senderId
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_chat_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_chat_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder.itemViewType == VIEW_TYPE_SENT) {
            (holder as SentMessageViewHolder).bind(message)
        } else {
            (holder as ReceivedMessageViewHolder).bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    // 시간 포맷팅 유틸리티 함수 (백엔드의 LocalDateTime 문자열을 파싱)
    private fun formatTimestamp(sentAt: String): String {
        return try {
            val dateTime = LocalDateTime.parse(sentAt)
            dateTime.format(timeFormatter)
        } catch (e: Exception) {
            "시간 오류"
        }
    }
}