// com.longtoast.bilbil.NewPostFragment.kt
package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.longtoast.bilbil.databinding.ActivityNewPostFragmentBinding
// Retrofit 및 DTO Import
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// 💡 [핵심 수정] Java/Kotlin List 타입 충돌 해결을 위한 Import (이전 시도와 다름)
import kotlin.collections.List as KList
import java.util.List as JList

// 🚨 클래스 정의를 하나로 통합합니다.
class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

    // 1. 상태 관리를 위한 변수 정의
    private var productStatus: String = "AVAILABLE"
    private var selectedPriceUnit: String = "" // 선택된 단위 (일, 월, 시간)
    private var rentalPriceString: String = "" // 입력된 가격 값 (문자열)

    // Activity Result Launcher 정의
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(requireContext(), "사진 첨부 완료!", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityNewPostFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 초기 가격 힌트 설정
        updatePriceTextView()

        // 1. 작성 완료 버튼 클릭 리스너 설정
        binding.completeButton.setOnClickListener {
            submitPost()
        }

        // 2. 닫기 버튼 클릭 리스너 설정
        binding.closeButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 3. 카메라 영역 클릭 리스너 설정
        binding.layoutCameraArea.setOnClickListener {
            openGalleryForImage()
        }

        // 4. 거래 희망 지역 클릭 리스너 설정
        binding.textViewAddress.setOnClickListener {
            val intent = Intent(requireContext(), SettingMapActivity::class.java)
            startActivity(intent)
            Toast.makeText(requireContext(), "지도 설정 화면으로 이동", Toast.LENGTH_SHORT).show()
        }

        // 5. 대여 가격 입력 필드 클릭 리스너 (팝업 호출)
        binding.editTextPrice.setOnClickListener {
            showPriceUnitSelectionDialog()
        }

        // 6. 대여 상태 토글 그룹 리스너 설정
        setupStatusToggleGroup()
    }

    // PriceUnitListener 인터페이스 구현 (팝업에서 결과를 받아옴)
    override fun onPriceUnitSelected(price: String, unit: String) {
        rentalPriceString = price
        selectedPriceUnit = unit
        updatePriceTextView() // 팝업이 닫힐 때 TextView 업데이트
    }

    // 팝업을 띄우는 함수
    private fun showPriceUnitSelectionDialog() {
        val dialog = PriceUnitDialogFragment()
        dialog.show(childFragmentManager, "PriceUnitDialog")
    }

    // 가격 TextView (EditText)를 업데이트하는 함수
    private fun updatePriceTextView() {
        if (rentalPriceString.isEmpty()) {
            binding.editTextPrice.hint = "₩ 대여 가격 (단위 선택)을 입력해주세요."
        } else {
            binding.editTextPrice.setText("₩ ${rentalPriceString} / ${selectedPriceUnit}")
        }
    }


    /**
     * MaterialButtonToggleGroup 리스너 설정 및 초기 상태 지정
     */
    private fun setupStatusToggleGroup() {
        binding.toggleStatusGroup.check(R.id.button_rent_available)

        binding.toggleStatusGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.button_rent_available -> {
                        productStatus = "AVAILABLE"
                        Toast.makeText(requireContext(), "상태: 대여 가능", Toast.LENGTH_SHORT).show()
                    }
                    R.id.button_rent_unavailable -> {
                        productStatus = "UNAVAILABLE"
                        Toast.makeText(requireContext(), "상태: 대여중", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    private fun openGalleryForImage() {
        galleryLauncher.launch("image/*")
    }


    /**
     * 게시글 제목과 내용을 검증하고 서버로 데이터를 전송하는 함수
     */
    private fun submitPost() {
        // 1. 데이터 수집
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val depositText = binding.editTextDeposit.text.toString().trim()

        // 🚨 주소값 수집: 임시 데이터로 대체 (유효성 검사 무시)
        val address = "서울 구로구 오류동"

        // 2. 입력 값 검증 (Validation)
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            binding.editTextTitle.requestFocus()
            return
        }
        if (category.isEmpty()) {
            Toast.makeText(requireContext(), "카테고리를 입력해주세요.", Toast.LENGTH_SHORT).show()
            binding.editTextCategory.requestFocus()
            return
        }
        if (rentalPriceString.isEmpty()) {
            Toast.makeText(requireContext(), "대여 가격을 설정해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. 데이터 변환 및 DTO 생성
        val price = rentalPriceString.toIntOrNull() ?: 0
        val deposit: Int? = depositText.toIntOrNull()

        // TODO: 이미지 업로드 로직으로 얻은 실제 URL 목록으로 대체해야 합니다. (임시 데이터 사용)
        // 💡 [수정] Kotlin List 타입이 DTO 생성자에 전달되도록 수정
        val imageUrls: KList<String> = listOf(
            "https://bilbil-bucket.s3.ap-northeast-2.amazonaws.com/temp_image1.jpg",
            "https://bilbil-bucket.s3.ap-northeast-2.amazonaws.com/temp_image2.jpg"
        )

        // DTO 정의가 (val imageUrls: List<String>)일 때, Kotlin List를 전달
        val request = ProductCreateRequest(
            title = title,
            price = price,
            description = description,
            category = category,
            status = productStatus,
            deposit = deposit,
            imageUrls = imageUrls,
            address = address
        )

        // 4. Retrofit 서버 통신 실행
        // 이 시점에서 오류가 발생한다면, DTO의 필드명/타입이 백엔드와 불일치하는 경우 외에는 없음.
        RetrofitClient.getApiService().createProduct(request)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "게시글이 성공적으로 등록되었습니다!", Toast.LENGTH_LONG).show()
                        parentFragmentManager.popBackStack() // 성공 시 화면 닫기
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("POST_API", "게시글 등록 실패: ${response.code()}, 메시지: $errorBody")
                        Toast.makeText(requireContext(), "등록 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("POST_API", "서버 통신 오류", t)
                    Toast.makeText(requireContext(), "서버 연결 오류 발생", Toast.LENGTH_LONG).show()
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}