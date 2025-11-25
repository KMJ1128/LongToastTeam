// com.longtoast.bilbil.ChatAdapter.kt (수정)
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
import android.util.Base64
import android.graphics.BitmapFactory
import android.widget.ImageView

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String // 🔑 현재 사용자 ID (String)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    // 💡 [핵심 수정] String인 currentUserId를 Int로 안전하게 변환하여 비교에 사용
    private val currentUserIdInt: Int? = currentUserId.toIntOrNull()

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())

    // 💡 Base64 디코딩 및 이미지 설정을 위한 헬퍼 함수 (유지)
    private fun setImageViewFromBase64(imageView: ImageView, base64Data: String?) {
        if (base64Data.isNullOrEmpty()) {
            imageView.visibility = View.GONE
            return
        }

        val cleanBase64 = if (base64Data.startsWith("data:")) {
            base64Data.substringAfterLast("base64,")
        } else {
            base64Data
        }

        try {
            val imageBytes = Base64.decode(cleanBase64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("ChatAdapter", "Base64 디코딩 실패", e)
            imageView.visibility = View.GONE
        }
    }


    // 1. 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)

        fun bind(message: ChatMessage) {
            imageAttachment?.let { setImageViewFromBase64(it, message.imageUrl) }

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
            imageAttachment?.let { setImageViewFromBase64(it, message.imageUrl) }

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

        // 🚨 [핵심 디버그 로그 추가]
        Log.d("CHAT_ADAPTER_VIEW", "Checking pos $position: MsgSenderID=${message.senderId}, CurrentID=${currentUserIdInt}. IsSent=${message.senderId == currentUserIdInt}")

        // Int 대 Int 비교
        if (message.senderId == currentUserIdInt) {
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