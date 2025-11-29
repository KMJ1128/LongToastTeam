package com.longtoast.bilbil

import android.content.Intent
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

    private var partnerNickname: String? = null
    private var partnerProfileImageUrl: String? = null

    private val serverFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("a h:mm", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateHeaderFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
    private val gson = Gson()

    private val actionPrefix = "[RENT_CONFIRM]"
    private val numberFormat = java.text.DecimalFormat("#,###")

    private var confirmedPayload: RentalActionPayload? = null

    fun setPartnerInfo(nickname: String?, profileImageUrl: String?) {
        partnerNickname = nickname
        partnerProfileImageUrl = profileImageUrl
        notifyDataSetChanged()
    }

    private fun resolveImageUrl(relativeOrFull: String?): String? {
        if (relativeOrFull.isNullOrEmpty()) return null
        return ImageUrlUtils.resolve(relativeOrFull)
    }

    // =====================================================
    // SENT MESSAGE VIEW HOLDER
    // =====================================================
    inner class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val readText: TextView = view.findViewById(R.id.text_read_status)
        private val messageText: TextView = view.findViewById(R.id.text_message_sent)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_sent)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_sent)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_sent)

        fun bind(message: ChatMessage, position: Int) {

            // 🔥 읽음/안읽음 표시
            readText.text = if (message.isRead == true) "읽음" else "안읽음"
            readText.visibility = View.VISIBLE

            // 텍스트 메시지
            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            // 이미지 메시지
            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty()) {
                imageAttachment?.visibility = View.VISIBLE
                Glide.with(imageAttachment!!.context).load(fullUrl).into(imageAttachment)

                imageAttachment.setOnClickListener {
                    openImageFullscreen(it, fullUrl)
                }
            } else {
                imageAttachment?.visibility = View.GONE
                imageAttachment?.setOnClickListener(null)
            }

            timestampText.text = formatTime(message.sentAt)
            bindDateHeader(dateHeader, position, message)
        }
    }

    // =====================================================
    // RECEIVED MESSAGE VIEW HOLDER
    // =====================================================
    inner class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val messageText: TextView = view.findViewById(R.id.text_message_received)
        private val timestampText: TextView = view.findViewById(R.id.text_timestamp_received)
        private val nicknameText: TextView = view.findViewById(R.id.text_nickname_received)
        private val imageAttachment: ImageView? = view.findViewById(R.id.image_attachment_received)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_received)
        private val profileImage: ImageView = view.findViewById(R.id.image_profile_received)

        fun bind(message: ChatMessage, position: Int) {

            nicknameText.text = partnerNickname ?: "상대방"

            // 상대방 프로필
            val profileUrl = resolveImageUrl(partnerProfileImageUrl)
            Glide.with(profileImage.context)
                .load(profileUrl)
                .placeholder(R.drawable.no_profile)
                .circleCrop()
                .into(profileImage)

            if (!message.content.isNullOrEmpty()) {
                messageText.text = message.content
                messageText.visibility = View.VISIBLE
            } else {
                messageText.visibility = View.GONE
            }

            val fullUrl = resolveImageUrl(message.imageUrl)
            if (!fullUrl.isNullOrEmpty()) {
                imageAttachment?.visibility = View.VISIBLE
                Glide.with(imageAttachment!!.context).load(fullUrl).into(imageAttachment)

                imageAttachment.setOnClickListener {
                    openImageFullscreen(it, fullUrl)
                }
            } else {
                imageAttachment?.visibility = View.GONE
                imageAttachment?.setOnClickListener(null)
            }

            timestampText.text = formatTime(message.sentAt)
            bindDateHeader(dateHeader, position, message)
        }
    }

    // =====================================================
    // RENT ACTION VIEW HOLDER
    // =====================================================
    inner class RentalActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val prompt: TextView = view.findViewById(R.id.text_rental_prompt)
        private val confirmButton: Button = view.findViewById(R.id.button_confirm_rental)
        private val dateHeader: TextView = view.findViewById(R.id.text_date_header_action)

        fun bind(message: ChatMessage, position: Int) {

            val payload = parseActionPayload(message.content)
            val isSender = message.senderId == currentUserIdInt

            val rentInfo = payload?.let {
                "기간: ${it.startDate} ~ ${it.endDate}\n" +
                        "거래 방식: ${it.deliveryMethod ?: "미입력"}\n" +
                        "총 결제 예상: ${numberFormat.format(it.totalAmount)}원"
            } ?: ""

            prompt.text =
                "만약 다음 대여 조건에 동의하신다면\n아래 버튼을 눌러 대여를 확정하세요.\n\n$rentInfo"

            if (payload != null && confirmedPayload?.startDate == payload.startDate) {
                confirmButton.text = "대여 확정 완료"
                confirmButton.isEnabled = false
            } else {
                confirmButton.text = if (isSender) "요청 전송됨" else "대여 확정하기"
                confirmButton.isEnabled = !isSender && payload != null
            }

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

    // =====================================================
    // ADAPTER CORE
    // =====================================================
    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        val content = msg.content?.trimStart() ?: ""
        return when {
            content.startsWith(actionPrefix) -> VIEW_TYPE_RENT_ACTION
            msg.senderId == currentUserIdInt -> VIEW_TYPE_SENT
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

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val msg = messages[pos]
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as SentMessageViewHolder).bind(msg, pos)
            VIEW_TYPE_RECEIVED -> (holder as ReceivedMessageViewHolder).bind(msg, pos)
            VIEW_TYPE_RENT_ACTION -> (holder as RentalActionViewHolder).bind(msg, pos)
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private fun parseActionPayload(content: String?): RentalActionPayload? {
        if (content.isNullOrEmpty()) return null
        val clean = content.trimStart()
        if (!clean.startsWith(actionPrefix)) return null
        return try {
            gson.fromJson(clean.removePrefix(actionPrefix), RentalActionPayload::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun markRentalConfirmed(payload: RentalActionPayload) {
        confirmedPayload = payload
        notifyDataSetChanged()
    }

    private fun formatTime(iso: String?): String {
        return try {
            val date = serverFormat.parse(iso ?: "") ?: return ""
            displayFormat.format(date)
        } catch (_: Exception) {
            ""
        }
    }

    private fun bindDateHeader(view: TextView, pos: Int, msg: ChatMessage) {
        val key = getDateKey(msg)
        val prev = if (pos > 0) getDateKey(messages[pos - 1]) else null
        if (key != null && key != prev) {
            view.visibility = View.VISIBLE
            val date = serverFormat.parse(msg.sentAt)
            view.text = date?.let { dateHeaderFormat.format(it) } ?: key
        } else view.visibility = View.GONE
    }

    private fun getDateKey(msg: ChatMessage): String? {
        return try {
            serverFormat.parse(msg.sentAt)?.let { dateKeyFormat.format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun openImageFullscreen(view: View, imageUrl: String) {
        val context = view.context
        val intent = Intent(context, ImagePreviewActivity::class.java)
        intent.putExtra("IMAGE_URL", imageUrl)
        context.startActivity(intent)
    }
}
