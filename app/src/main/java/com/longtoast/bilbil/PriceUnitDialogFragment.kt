package com.longtoast.bilbil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class PriceUnitDialogFragment : DialogFragment() {

    // 팝업 결과를 NewPostFragment로 전달하기 위한 인터페이스
    interface PriceUnitListener {
        fun onPriceUnitSelected(price: String, unit: String)
    }

    private var priceUnitListener: PriceUnitListener? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        // 리스너를 Fragment의 부모에 연결합니다.
        if (parentFragment is PriceUnitListener) {
            priceUnitListener = parentFragment as PriceUnitListener
        } else {
            // 디버깅용: 부모 Fragment가 리스너를 구현했는지 확인
            // throw RuntimeException("Parent fragment must implement PriceUnitListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // dialog_price_unit.xml 레이아웃 사용
        return inflater.inflate(R.layout.dialog_price_unit, container, false)
    }

    // 팝업의 너비를 화면에 맞게 조정
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editPrice = view.findViewById<EditText>(R.id.edit_price_dialog)
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.radio_group_unit)
        val btnConfirm = view.findViewById<android.widget.Button>(R.id.button_confirm_price)

        btnConfirm.setOnClickListener {
            val price = editPrice.text.toString().trim()
            if (price.isEmpty()) {
                Toast.makeText(requireContext(), "가격을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 선택된 라디오 버튼의 ID 확인
            val selectedUnitId = radioGroup.checkedRadioButtonId
            val selectedUnitText = when (selectedUnitId) {
                R.id.radio_day -> "일"
                R.id.radio_month -> "월"
                R.id.radio_hour -> "시간"
                else -> {
                    Toast.makeText(requireContext(), "단위를 선택해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // NewPostFragment로 결과 전달
            priceUnitListener?.onPriceUnitSelected(price, selectedUnitText)
            dismiss() // 다이얼로그 닫기
        }
    }
}
