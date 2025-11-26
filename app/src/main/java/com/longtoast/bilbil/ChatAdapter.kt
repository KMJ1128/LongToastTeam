package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.dto.ChatMessage
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String // 🔑 현재 사용자 ID (String)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    // 💡 String인 currentUserId를 Int로 변환해서 비교에 사용
    private val currentUserIdInt: Int? = currentUserId.toIntOrNull()

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())

    // 서버 상대 경로를 절대 URL로 변환
    private fun resolveImageUrl(relativeOrFull: String?): String? {
        if (relativeOrFull.isNullOrEmpty()) return null

        return if (relativeOrFull.startsWith("/")) {
            // "/uploads/..." 형태 → HTTP_BASE_URL과 결합
            ServerConfig.HTTP_BASE_URL.removeSuffix("/") + relativeOrFull
        } else {
            // 이미 http로 시작하면 그대로 사용
            relativeOrFull
        }
    }

    // 🔹 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)

        fun bind(message: ChatMessage) {
            // 텍스트
            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            // 이미지
            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty() && imageAttachment != null) {
                imageAttachment.visibility = View.VISIBLE
                Glide.with(imageAttachment.context)
                    .load(fullUrl)
                    .into(imageAttachment)
            } else {
                imageAttachment?.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
        }
    }

    // 🔹 받은 메시지 ViewHolder
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)

        fun bind(message: ChatMessage) {
            // 텍스트
            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            // 이미지
            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty() && imageAttachment != null) {
                imageAttachment.visibility = View.VISIBLE
                Glide.with(imageAttachment.context)
                    .load(fullUrl)
                    .into(imageAttachment)
            } else {
                imageAttachment?.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
            nicknameText.text = "상대방(${message.senderId})"
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        // 디버그용 로그
        Log.d(
            "CHAT_ADAPTER_VIEW",
            "Checking pos $position: MsgSenderID=${message.senderId}, CurrentID=$currentUserIdInt, IsSent=${message.senderId == currentUserIdInt}"
        )

        return if (message.senderId == currentUserIdInt) {
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
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(message)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message)
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
