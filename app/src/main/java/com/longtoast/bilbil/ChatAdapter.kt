// com.longtoast.bilbil.ChatAdapter.kt
package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longtoast.bilbil.dto.ChatMessage
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String // 🔑 현재 사용자 ID (String "1")
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())


    // 1. 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            timestampText.text = formatTime(message.sentAt)
        }
    }

    // 2. 받은 메시지 ViewHolder
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            timestampText.text = formatTime(message.sentAt)
            nicknameText.text = "상대방(${message.senderId})" // 임시 표시
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        // 🔑 [최종 비교 로직] DTO의 Int senderId를 String으로 변환하여 현재 사용자 ID와 비교
        val messageSenderIdString = message.senderId.toString()

        if (messageSenderIdString == currentUserId) {
            return VIEW_TYPE_SENT
        } else {
            return VIEW_TYPE_RECEIVED
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

    private fun formatTime(isoTimeString: String?): String {
        return try {
            if (isoTimeString.isNullOrEmpty()) return ""
            val date = serverFormat.parse(isoTimeString) ?: return "시간 오류"
            displayFormat.format(date)
        } catch (e: Exception) {
            Log.e("ChatAdapter", "시간 파싱 오류: $isoTimeString", e)
            "시간 오류"
        }
    }
}