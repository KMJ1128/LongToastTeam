package com.longtoast.bilbil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.ImageUrlUtils
import com.google.gson.Gson
import com.longtoast.bilbil.dto.RentalActionPayload
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String, // 🔑 현재 사용자 ID (String)
    private val onRentalConfirm: ((RentalActionPayload) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val VIEW_TYPE_RENT_ACTION = 3

    // 💡 String인 currentUserId를 Int로 변환해서 비교에 사용
    private val currentUserIdInt: Int? = currentUserId.toIntOrNull()

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateHeaderFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
    private val gson = Gson()
    private val actionPrefix = "[RENT_CONFIRM]"

    // 서버 상대 경로를 절대 URL로 변환
    private fun resolveImageUrl(relativeOrFull: String?): String? {
        if (relativeOrFull.isNullOrEmpty()) return null

        return ImageUrlUtils.resolve(relativeOrFull)
    }

    // 🔹 보낸 메시지 ViewHolder
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_sent)

        fun bind(message: ChatMessage, position: Int) {
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
            bindDateHeader(dateHeader, position, message)
        }
    }

    // 🔹 받은 메시지 ViewHolder
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_received)

        fun bind(message: ChatMessage, position: Int) {
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
            bindDateHeader(dateHeader, position, message)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        // 디버그용 로그
        Log.d(
            "CHAT_ADAPTER_VIEW",
            "Checking pos $position: MsgSenderID=${message.senderId}, CurrentID=$currentUserIdInt, IsSent=${message.senderId == currentUserIdInt}"
        )

        return when {
            message.content?.startsWith(actionPrefix) == true -> VIEW_TYPE_RENT_ACTION
            message.senderId == currentUserIdInt -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_chat_message_sent, parent, false)
                SentMessageViewHolder(view)
            }

            VIEW_TYPE_RENT_ACTION -> {
                val view = inflater.inflate(R.layout.item_chat_rental_action, parent, false)
                RentalActionViewHolder(view)
            }

            else -> {
                val view = inflater.inflate(R.layout.item_chat_message_received, parent, false)
                ReceivedMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(message, position)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(message, position)
            VIEW_TYPE_RENT_ACTION -> (holder as RentalActionViewHolder).bind(message, position)
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

    private fun bindDateHeader(headerView: TextView, position: Int, message: ChatMessage) {
        val currentKey = getDateKey(message)
        val previousKey = if (position > 0) getDateKey(messages[position - 1]) else null

        if (currentKey != null && currentKey != previousKey) {
            headerView.visibility = View.VISIBLE
            val parsedDate = try { serverFormat.parse(message.sentAt) } catch (e: Exception) { null }
            headerView.text = parsedDate?.let { dateHeaderFormat.format(it) } ?: currentKey
        } else {
            headerView.visibility = View.GONE
        }
    }

    private fun getDateKey(message: ChatMessage): String? {
        return try {
            val date = serverFormat.parse(message.sentAt)
            if (date != null) dateKeyFormat.format(date) else null
        } catch (e: Exception) {
            null
        }
    }

    inner class RentalActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val prompt: TextView = view.findViewById(R.id.text_rental_prompt)
        private val confirmButton: Button = view.findViewById(R.id.button_confirm_rental)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_action)

        fun bind(message: ChatMessage, position: Int) {
            val payload = parseActionPayload(message.content)
            val isSender = message.senderId == currentUserIdInt

            val rentInfo = payload?.let {
                "기간: ${it.startDate} ~ ${it.endDate}\n거래 방식: ${it.deliveryMethod}\n총 결제 예상: ${numberFormat.format(it.totalAmount)}원"
            } ?: ""

            prompt.text = "상대방으로부터 대여 확인 요청이 들어왔습니다. 동의 하십니까?\n$rentInfo"

            confirmButton.text = if (isSender) "요청 전송됨" else "대여 확정하기"
            confirmButton.isEnabled = !isSender && payload != null
            confirmButton.setOnClickListener {
                payload?.let { action -> onRentalConfirm?.invoke(action) }
            }

            bindDateHeader(dateHeader, position, message)
        }
    }

    private fun parseActionPayload(content: String?): RentalActionPayload? {
        if (content.isNullOrEmpty() || !content.startsWith(actionPrefix)) return null
        val json = content.removePrefix(actionPrefix)
        return try {
            gson.fromJson(json, RentalActionPayload::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private val numberFormat = java.text.DecimalFormat("#,###")
}
