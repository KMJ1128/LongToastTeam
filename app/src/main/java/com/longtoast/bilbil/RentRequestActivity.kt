package com.longtoast.bilbil

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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

    private var pricePerUnit = 0
    private var priceUnitType = 1   // 1=일, 2=월, 3=시간
    private var deposit = 0
    private var itemId = -1
    private var lenderId = -1
    private var sellerNickname: String? = null
    private var selectedDays: Int = 0
    private var lastRentFee: Int = 0
    private var lastTotalAmount: Int = 0
    private var lastTransactionId: Long? = null

    private var startCalendar: Calendar? = null
    private var endCalendar: Calendar? = null
    private var imageUrl: String? = null

    private var selectedDeliveryMethod: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRentRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ------------------------------
        // Intent 데이터 수신
        // ------------------------------
        val title = intent.getStringExtra("TITLE") ?: "상품 정보 없음"
        pricePerUnit = intent.getIntExtra("PRICE", 0)
        priceUnitType = intent.getIntExtra("PRICE_UNIT", 1)
        deposit = intent.getIntExtra("DEPOSIT", 0)
        itemId = intent.getIntExtra("ITEM_ID", -1)
        lenderId = intent.getIntExtra("LENDER_ID", -1)
        sellerNickname = intent.getStringExtra("SELLER_NICKNAME")
        // val imageUrl = intent.getStringExtra("IMAGE_URL")

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textProductTitle.text = title

        val unitLabel = PriceUnitMapper.toLabel(priceUnitType)
        binding.textProductPrice.text = "${numberFormat.format(pricePerUnit)}원 / $unitLabel"

        updatePriceUI(0)

        imageUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_default_category)
                .into(binding.imageProductThumbnail)
        }

        // ------------------------------
        // ⭐ 날짜 선택 처리
        // ------------------------------
        binding.textStartDate.setOnClickListener {
            when (priceUnitType) {
                1 -> pickStartDate() // 일
                2 -> pickStartDate { showMonthPickerDialog() } // 월
                3 -> pickDate { cal ->
                    startCalendar = cal
                    pickTime(startCalendar!!) {
                        binding.textStartDate.text = formatDateTime(startCalendar!!)
                        promptEndDateTime()
                    }
                }
            }
        }

        binding.textEndDate.setOnClickListener {
            when (priceUnitType) {
                1 -> pickEndDate()

                2 -> Toast.makeText(this, "월 단위는 종료일 선택이 필요 없습니다.", Toast.LENGTH_SHORT).show()

                3 -> {
                    if (startCalendar == null) {
                        Toast.makeText(this, "시작 날짜/시간을 먼저 선택해주세요.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    pickDate { cal ->
                        endCalendar = cal
                        pickTime(endCalendar!!) {
                            binding.textEndDate.text = formatDateTime(endCalendar!!)
                            calculateHours()
                        }
                    }
                }
            }
        }

        // ------------------------------
        // 거래 방식
        // ------------------------------
        binding.radioGroupDelivery.setOnCheckedChangeListener { _, id ->
            selectedDeliveryMethod = when (id) {
                R.id.radio_direct -> "DIRECT"
                R.id.radio_parcel -> "PARCEL"
                else -> null
            }
        }

        // ------------------------------
        // 추가 비용
        // ------------------------------
        binding.radioGroupExtraFee.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.radio_extra_none -> {
                    extraFee = 0
                    binding.layoutExtraFee.visibility = View.GONE
                    binding.inputExtraFee.setText("")
                    binding.textExtraFee.text = "0원"
                    updatePriceUI(selectedUnits)
                }

                R.id.radio_extra_yes -> {
                    binding.layoutExtraFee.visibility = View.VISIBLE
                }
            }
        }

        binding.inputExtraFee.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                extraFee = s.toString().toIntOrNull() ?: 0
                binding.textExtraFee.text = "${numberFormat.format(extraFee)}원"
                updatePriceUI(selectedUnits)
            }
            override fun beforeTextChanged(s: CharSequence?, s1: Int, s2: Int, s3: Int) {}
            override fun onTextChanged(s: CharSequence?, s1: Int, s2: Int, s3: Int) {}
        })

        // ------------------------------
        // 요청 버튼
        // ------------------------------
        binding.btnSubmitRent.setOnClickListener {

            if (priceUnitType == 1) {
                if (startCalendar == null || endCalendar == null) {
                    Toast.makeText(this, "대여 날짜를 모두 선택해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (priceUnitType == 3 && (startCalendar == null || endCalendar == null)) {
                Toast.makeText(this, "시간 단위는 시작/종료 시간을 모두 선택해야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedDeliveryMethod == null) {
                Toast.makeText(this, "거래 방식을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendRentRequest(title)
        }
    }

    private fun pickEndDate() {
        showDatePicker { y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d, 0, 0, 0)
            endCalendar = cal
            binding.textEndDate.text = "%04d-%02d-%02d".format(y, m + 1, d)

            calculateDays()
        }
    }


    private fun showDatePicker(onDateSelected: (Int, Int, Int) -> Unit) {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d -> onDateSelected(y, m, d) },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }


    private fun pickDate(onPicked: (Calendar) -> Unit) {
        showDatePicker { y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            onPicked(cal)
        }
    }


    private fun pickTime(calendar: Calendar, onPicked: () -> Unit) {
        TimePickerDialog(
            this,
            { _, h, min ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, min)
                onPicked()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }


    // ======================================================================
    // ⭐ 월 단위 선택
    // ======================================================================
    private fun showMonthPickerDialog() {
        val months = (1..12).map { "${it}개월" }.toTypedArray()
        var picked = 1

        AlertDialog.Builder(this)
            .setTitle("대여 개월 선택")
            .setSingleChoiceItems(months, 0) { _, i ->
                picked = i + 1
            }
            .setPositiveButton("확인") { _, _ ->
                selectedUnits = picked

                val cal = Calendar.getInstance()
                cal.time = startCalendar!!.time
                cal.add(Calendar.MONTH, picked)
                endCalendar = cal

                binding.textEndDate.text = formatDate(endCalendar!!)

                updatePriceUI(selectedUnits)
            }
            .show()
    }


    // ======================================================================
    // ⭐ 시간 단위 계산 (정확한 시간)
    // ======================================================================
    private fun calculateHours() {
        if (startCalendar == null || endCalendar == null) return

        if (endCalendar!!.timeInMillis <= startCalendar!!.timeInMillis) {
            Toast.makeText(this, "반납 시간은 시작 시간 이후여야 합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val diffMillis = endCalendar!!.timeInMillis - startCalendar!!.timeInMillis
        selectedUnits = (diffMillis / (1000 * 60 * 60)).toInt() // 시간 단위

        updatePriceUI(selectedUnits)
    }

    private fun updatePriceUI(days: Int) {
        selectedDays = days
        val rentFee = pricePerDay * days
        lastRentFee = rentFee
        val totalAmount = rentFee + deposit
        lastTotalAmount = totalAmount

    // ======================================================================
    // ⭐ 일 단위 계산
    // ======================================================================
    private fun calculateDays() {
        if (startCalendar == null || endCalendar == null) return

        if (endCalendar!!.before(startCalendar)) {
            Toast.makeText(this, "반납일은 시작일 이후여야 합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val diffMillis = endCalendar!!.timeInMillis - startCalendar!!.timeInMillis
        selectedUnits = (TimeUnit.MILLISECONDS.toDays(diffMillis) + 1).toInt()

        updatePriceUI(selectedUnits)
    }


    private fun formatDateTime(c: Calendar): String =
        "%04d-%02d-%02d %02d:%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE)
        )

    private fun formatDate(c: Calendar): String =
        "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )


    // ======================================================================
    // ⭐ UI 금액 업데이트
    // ======================================================================
    private fun updatePriceUI(units: Int) {
        val unitLabel = PriceUnitMapper.toLabel(priceUnitType)

        val rentFee = pricePerUnit * units
        lastRentFee = rentFee

        val totalAmount = rentFee + deposit + extraFee
        lastTotalAmount = totalAmount

        binding.textDaysCount.text = "$unitLabel × $units"
        binding.textRentFee.text = "${numberFormat.format(rentFee)}원"
        binding.textDepositFee.text = "${numberFormat.format(deposit)}원"
        binding.textExtraFee.text = "${numberFormat.format(extraFee)}원"
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
        val content = "[RENT_CONFIRM]" + gson.toJson(payload)

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
