// com.longtoast.bilbil.ChatAdapter.kt
package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.R
import com.longtoast.bilbil.dto.ChatMessage
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())

    /**
     * 서버에서 오는 imageUrl이 "/uploads/..." 같은 상대 경로이기 때문에
     * 절대경로(도메인)와 합쳐서 Glide에 전달해준다.
     */
    private fun buildFullImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null

        // 이미 http(s)로 시작하면 그대로 사용
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return rawUrl
        }

        // 상대경로인 경우, ServerConfig.HTTP_BASE_URL 기준으로 붙여줌
        val base = ServerConfig.HTTP_BASE_URL.removeSuffix("/")
        return if (rawUrl.startsWith("/")) {
            base + rawUrl
        } else {
            "$base/$rawUrl"
        }
    }

    private fun setImageViewFromUrl(imageView: ImageView?, imageUrl: String?) {
        if (imageView == null) return

        val fullUrl = buildFullImageUrl(imageUrl)

        if (fullUrl.isNullOrBlank()) {
            imageView.visibility = View.GONE
            return
        }

        imageView.visibility = View.VISIBLE
        Glide.with(imageView.context)
            .load(fullUrl)
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .into(imageView)
    }

    // 1. 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)

        fun bind(message: ChatMessage) {
            setImageViewFromUrl(imageAttachment, message.imageUrl)

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
        }
    }

    // 2. 받은 메시지 ViewHolder
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)

        fun bind(message: ChatMessage) {
            setImageViewFromUrl(imageAttachment, message.imageUrl)

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            timestampText.text = formatTime(message.sentAt)
            nicknameText.text = "상대방(${message.senderId})"
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        Log.d(
            "CHAT_ADAPTER_VIEW",
            "Checking pos $position: MsgSenderID=${message.senderId}, CurrentID=$currentUserId. IsSent=${message.senderId == currentUserId}"
        )

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
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(message)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatTime(isoTimeString: String?): String {
        return try {
            if (isoTimeString.isNullOrEmpty()) return ""
            // "2025-11-25T15:44:56.1356541" 같은 형식에서 초까지 자른 후 파싱
            val normalized = if (isoTimeString.length >= 19) {
                isoTimeString.substring(0, 19) // yyyy-MM-ddTHH:mm:ss
            } else {
                isoTimeString
            }
            val date = serverFormat.parse(normalized) ?: return "시간 오류"
            displayFormat.format(date)
        } catch (e: Exception) {
            Log.e("ChatAdapter", "시간 파싱 오류: $isoTimeString", e)
            "시간 오류"
        }
    }
}
