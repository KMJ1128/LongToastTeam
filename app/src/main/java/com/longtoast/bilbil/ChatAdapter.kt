package com.longtoast.bilbil

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.RentalActionPayload
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUserId: String,
    private val onRentalConfirm: ((RentalActionPayload) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2
    private val VIEW_TYPE_RENT_ACTION = 3

    private val currentUserIdInt: Int? = currentUserId.toIntOrNull()

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateHeaderFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
    private val gson = Gson()

    private val actionPrefix = "[RENT_CONFIRM]"

    private fun resolveImageUrl(relativeOrFull: String?): String? {
        if (relativeOrFull.isNullOrEmpty()) return null
        return ImageUrlUtils.resolve(relativeOrFull)
    }

    // -----------------------------
    // Sent ViewHolder
    // -----------------------------
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_sent)

        fun bind(message: ChatMessage, position: Int) {

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else messageText.visibility = View.GONE

            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty()) {
                imageAttachment?.visibility = View.VISIBLE
                Glide.with(imageAttachment!!.context).load(fullUrl).into(imageAttachment)
            } else imageAttachment?.visibility = View.GONE

            timestampText.text = formatTime(message.sentAt)
            bindDateHeader(dateHeader, position, message)
        }
    }

    // -----------------------------
    // Received ViewHolder
    // -----------------------------
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_received)

        fun bind(message: ChatMessage, position: Int) {

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else messageText.visibility = View.GONE

            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty()) {
                imageAttachment?.visibility = View.VISIBLE
                Glide.with(imageAttachment!!.context).load(fullUrl).into(imageAttachment)
            } else imageAttachment?.visibility = View.GONE

            timestampText.text = formatTime(message.sentAt)
            nicknameText.text = "상대방(${message.senderId})"
            bindDateHeader(dateHeader, position, message)
        }
    }

    // -----------------------------
    // RENT ACTION ViewHolder
    // -----------------------------
    inner class RentalActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val prompt: TextView = view.findViewById(R.id.text_rental_prompt)
        private val confirmButton: Button = view.findViewById(R.id.button_confirm_rental)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_action)

        fun bind(message: ChatMessage, position: Int) {

            // 🔥 핵심 — trimStart() 적용
            val payload = parseActionPayload(message.content)
            val isSender = message.senderId == currentUserIdInt

            val rentInfo = payload?.let {
                "기간: ${it.startDate} ~ ${it.endDate}\n" +
                        "거래 방식: ${it.deliveryMethod ?: "미입력"}\n" +
                        "총 결제 예상: ${numberFormat.format(it.totalAmount)}원"
            } ?: ""

            prompt.text =
                "만약 다음과 같은 대여에 동의하신다면\n" +
                        "서로 간 대여 확정을 위해서 다음의 버튼을 누르십시오\n\n$rentInfo"

            // 보낸 사람 → 버튼 비활성화
            confirmButton.text = if (isSender) "요청 전송됨" else "대여 확정하기"
            confirmButton.isEnabled = !isSender && payload != null

            confirmButton.setOnClickListener {
                payload?.let { action ->
                    confirmButton.isEnabled = false
                    confirmButton.text = "처리 중..."
                    onRentalConfirm?.invoke(action)
                }
            }

            bindDateHeader(dateHeader, position, message)
        }
    }

    // -----------------------------
    // ItemViewType
    // -----------------------------
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val content = message.content?.trimStart() ?: ""

        return when {
            content.startsWith(actionPrefix) -> VIEW_TYPE_RENT_ACTION
            message.senderId == currentUserIdInt -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (type) {
            VIEW_TYPE_SENT -> SentMessageViewHolder(inf.inflate(R.layout.item_chat_message_sent, parent, false))
            VIEW_TYPE_RENT_ACTION -> RentalActionViewHolder(inf.inflate(R.layout.item_chat_rental_action, parent, false))
            else -> ReceivedMessageViewHolder(inf.inflate(R.layout.item_chat_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val msg = messages[pos]
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(msg, pos)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(msg, pos)
            VIEW_TYPE_RENT_ACTION -> (holder as RentalActionViewHolder).bind(msg, pos)
        }
    }

    override fun getItemCount(): Int = messages.size

    // -----------------------------
    // Helper Methods
    // -----------------------------
    private fun formatTime(iso: String?): String {
        return try {
            if (iso.isNullOrEmpty()) return ""
            val date = serverFormat.parse(iso) ?: return ""
            displayFormat.format(date)
        } catch (e: Exception) {
            "시간 오류"
        }
    }

    private fun bindDateHeader(view: TextView, pos: Int, msg: ChatMessage) {
        val key = getDateKey(msg)
        val prev = if (pos > 0) getDateKey(messages[pos - 1]) else null

        if (key != null && key != prev) {
            view.visibility = View.VISIBLE
            val date = try { serverFormat.parse(msg.sentAt) } catch (e: Exception) { null }
            view.text = date?.let { dateHeaderFormat.format(it) } ?: key
        } else view.visibility = View.GONE
    }

    private fun getDateKey(msg: ChatMessage): String? {
        return try {
            serverFormat.parse(msg.sentAt)?.let { dateKeyFormat.format(it) }
        } catch (_: Exception) { null }
    }

    // 🔥 핵심: trimStart() 적용한 안전한 payload 파싱
    private fun parseActionPayload(content: String?): RentalActionPayload? {
        if (content.isNullOrEmpty()) return null

        val clean = content.trimStart()
        if (!clean.startsWith(actionPrefix)) return null

        val json = clean.removePrefix(actionPrefix)
        return try {
            gson.fromJson(json, RentalActionPayload::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val numberFormat = java.text.DecimalFormat("#,###")
}
