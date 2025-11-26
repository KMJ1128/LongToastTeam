package com.longtoast.bilbil

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.longtoast.bilbil.databinding.ActivityRentRequestBinding
import java.text.DecimalFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RentRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRentRequestBinding
    private val numberFormat = DecimalFormat("#,###")

    // 상품 정보
    private var pricePerDay = 0
    private var deposit = 0

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

            // 성공 로직
            val methodText = if (selectedDeliveryMethod == "DIRECT") "직거래" else "택배"
            Toast.makeText(this, "[$methodText] 대여 요청이 전송되었습니다.", Toast.LENGTH_LONG).show()

            // 추후 서버 API 호출 시 selectedDeliveryMethod 값도 같이 보내면 됩니다.
            finish()
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
        val rentFee = pricePerDay * days
        val totalAmount = rentFee + deposit

        binding.textDaysCount.text = "대여료 (${days}일)"
        binding.textRentFee.text = "${numberFormat.format(rentFee)}원"
        binding.textDepositFee.text = "${numberFormat.format(deposit)}원"
        binding.textTotalPrice.text = "${numberFormat.format(totalAmount)}원"
    }
}