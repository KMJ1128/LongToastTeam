package com.longtoast.bilbil

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityRentRequestBinding
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.ChatSendRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.RentalActionPayload
import com.longtoast.bilbil.dto.RentalRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DecimalFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RentRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRentRequestBinding
    private val numberFormat = DecimalFormat("#,###")

    // 상품 정보
    private var pricePerDay = 0
    private var deposit = 0
    private var itemId = -1
    private var lenderId = -1
    private var sellerNickname: String? = null
    private var selectedDays: Int = 0
    private var lastRentFee: Int = 0
    private var lastTotalAmount: Int = 0
    private var lastTransactionId: Long? = null

    // 날짜 정보
    private var startCalendar: Calendar? = null
    private var endCalendar: Calendar? = null

    // 🚨 [추가됨] 선택된 거래 방식 (null이면 미선택)
    private var selectedDeliveryMethod: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRentRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 데이터 수신
        val title = intent.getStringExtra("TITLE") ?: "상품 정보 없음"
        pricePerDay = intent.getIntExtra("PRICE", 0)
        deposit = intent.getIntExtra("DEPOSIT", 0)
        itemId = intent.getIntExtra("ITEM_ID", -1)
        lenderId = intent.getIntExtra("LENDER_ID", -1)
        sellerNickname = intent.getStringExtra("SELLER_NICKNAME")
        // val imageUrl = intent.getStringExtra("IMAGE_URL")

        // 2. UI 초기화
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textProductTitle.text = title
        binding.textProductPrice.text = "${numberFormat.format(pricePerDay)}원 / 일"

        updatePriceUI(0) // 초기화

        // 3. 날짜 선택 리스너
        binding.textStartDate.setOnClickListener {
            showDatePicker { year, month, day ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startCalendar = cal
                binding.textStartDate.text = String.format("%d-%02d-%02d", year, month + 1, day)
                calculateAndDisplay()
            }
        }

        binding.textEndDate.setOnClickListener {
            showDatePicker { year, month, day ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                endCalendar = cal
                binding.textEndDate.text = String.format("%d-%02d-%02d", year, month + 1, day)
                calculateAndDisplay()
            }
        }

        // 🚨 [추가됨] 거래 방식 라디오 버튼 리스너
        binding.radioGroupDelivery.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_direct -> selectedDeliveryMethod = "DIRECT"
                R.id.radio_parcel -> selectedDeliveryMethod = "PARCEL"
            }
        }

        // 4. 요청 버튼
        binding.btnSubmitRent.setOnClickListener {
            // 유효성 검사 1: 날짜
            if (startCalendar == null || endCalendar == null) {
                Toast.makeText(this, "대여 기간을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🚨 [추가됨] 유효성 검사 2: 거래 방식
            if (selectedDeliveryMethod == null) {
                Toast.makeText(this, "거래 방식을 선택해주세요 (직거래/택배).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendRentRequest(title)
        }
    }

    private fun showDatePicker(onDateSelected: (Int, Int, Int) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val dialog = DatePickerDialog(this, { _, y, m, d ->
            onDateSelected(y, m, d)
        }, year, month, day)
        dialog.datePicker.minDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun calculateAndDisplay() {
        val start = startCalendar
        val end = endCalendar

        if (start != null && end != null) {
            if (end.before(start)) {
                Toast.makeText(this, "반납일은 시작일 이후여야 합니다.", Toast.LENGTH_SHORT).show()
                binding.textEndDate.text = "반납일 선택"
                endCalendar = null
                updatePriceUI(0)
                return
            }

            val diffInMillis = end.timeInMillis - start.timeInMillis
            // 당일 대여 = 1일로 계산 (+1)
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffInMillis) + 1

            updatePriceUI(diffDays.toInt())
        }
    }

    private fun updatePriceUI(days: Int) {
        selectedDays = days
        val rentFee = pricePerDay * days
        lastRentFee = rentFee
        val totalAmount = rentFee + deposit
        lastTotalAmount = totalAmount

        binding.textDaysCount.text = "대여료 (${days}일)"
        binding.textRentFee.text = "${numberFormat.format(rentFee)}원"
        binding.textDepositFee.text = "${numberFormat.format(deposit)}원"
        binding.textTotalPrice.text = "${numberFormat.format(totalAmount)}원"
    }

    private fun sendRentRequest(title: String) {
        val borrowerId = AuthTokenManager.getUserId()
        val startText = binding.textStartDate.text.toString()
        val endText = binding.textEndDate.text.toString()

        if (borrowerId == null || itemId <= 0 || lenderId <= 0) {
            Toast.makeText(this, "로그인 또는 상품 정보를 확인해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = RentalRequest(
            itemId = itemId,
            lenderId = lenderId,
            borrowerId = borrowerId,
            startDate = startText,
            endDate = endText,
            rentFee = lastRentFee,
            deposit = deposit,
            totalAmount = lastTotalAmount,
            deliveryMethod = selectedDeliveryMethod ?: ""
        )

        RetrofitClient.getApiService().createRentalRequest(requestBody)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@RentRequestActivity, "대여 요청 저장 실패", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val raw = response.body()?.data ?: return
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val mapData: Map<String, Any> = gson.fromJson(gson.toJson(raw), type)
                    val transactionId = mapData["transactionId"]?.toString()?.toLongOrNull()
                    lastTransactionId = transactionId

                    if (transactionId == null) {
                        Toast.makeText(this@RentRequestActivity, "거래 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    createRoomAndSendMessages(title, transactionId)
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@RentRequestActivity, "대여 요청 저장 실패", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun createRoomAndSendMessages(title: String, transactionId: Long) {
        val borrowerId = AuthTokenManager.getUserId() ?: return
        val request = ChatRoomCreateRequest(itemId, lenderId, borrowerId)

        RetrofitClient.getApiService().createChatRoom(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(this@RentRequestActivity, "채팅방 생성 실패", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val raw = response.body()?.data ?: return
                    val gson = Gson()
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val mapData: Map<String, Any> = gson.fromJson(gson.toJson(raw), type)
                    val roomId = mapData["roomId"]?.toString()

                    if (roomId.isNullOrEmpty()) {
                        Toast.makeText(this@RentRequestActivity, "채팅방 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    RetrofitClient.getApiService()
                        .sendChatMessage(roomId, ChatSendRequest(buildMessage(title)))
                        .enqueue(object : Callback<MsgEntity> {
                            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                                if (response.isSuccessful) {
                                    sendActionPrompt(roomId, transactionId)
                                } else {
                                    Toast.makeText(this@RentRequestActivity, "메시지 전송 실패", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                                Toast.makeText(this@RentRequestActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                            }
                        })
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@RentRequestActivity, "채팅방 생성 실패", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun sendActionPrompt(roomId: String, transactionId: Long) {
        val gson = Gson()
        val payload = RentalActionPayload(
            transactionId = transactionId,
            itemId = itemId,
            startDate = binding.textStartDate.text.toString(),
            endDate = binding.textEndDate.text.toString(),
            rentFee = lastRentFee,
            deposit = deposit,
            totalAmount = lastTotalAmount,
            deliveryMethod = if (selectedDeliveryMethod == "DIRECT") "직거래" else "택배"
        )

        val infoText = """
            상대방으로부터 대여 확인 요청이 들어왔습니다. 동의 하십니까?
            아래 '대여 확정하기' 버튼을 눌러 거래를 확정하세요.
        """.trimIndent()

        RetrofitClient.getApiService()
            .sendChatMessage(roomId, ChatSendRequest(infoText))
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        sendActionCard(roomId, gson.toJson(payload))
                    } else {
                        Toast.makeText(this@RentRequestActivity, "안내 메시지 전송 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@RentRequestActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun sendActionCard(roomId: String, payloadJson: String) {
        val content = "[RENT_CONFIRM]$payloadJson"
        RetrofitClient.getApiService()
            .sendChatMessage(roomId, ChatSendRequest(content))
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RentRequestActivity, "대여 요청을 전송했습니다.", Toast.LENGTH_SHORT).show()
                        openChatRoom(roomId)
                    } else {
                        Toast.makeText(this@RentRequestActivity, "확정 요청 전송 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@RentRequestActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun buildMessage(title: String): String {
        val startText = binding.textStartDate.text.toString()
        val endText = binding.textEndDate.text.toString()
        val methodText = if (selectedDeliveryMethod == "DIRECT") "직거래" else "택배"

        return """
            [대여 요청]
            상품: $title
            기간: $startText ~ $endText (${selectedDays}일)
            거래 방식: $methodText
            대여료: ${numberFormat.format(lastRentFee)}원
            보증금: ${numberFormat.format(deposit)}원
            총 결제 예상: ${numberFormat.format(lastTotalAmount)}원
        """.trimIndent()
    }

    private fun openChatRoom(roomId: String) {
        val intent = Intent(this, ChatRoomActivity::class.java).apply {
            putExtra("ROOM_ID", roomId)
            putExtra("SELLER_NICKNAME", sellerNickname)
            putExtra("PRODUCT_ID", itemId)
        }
        startActivity(intent)
        finish()
    }
}
