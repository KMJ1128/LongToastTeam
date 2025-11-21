// com.longtoast.bilbil.NewPostFragment.kt (전체)
package com.longtoast.bilbil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.longtoast.bilbil.databinding.ActivityNewPostFragmentBinding
// Retrofit 및 DTO Import
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.graphics.Bitmap // 💡 Bitmap Import
import android.graphics.BitmapFactory // 💡 BitmapFactory Import
import java.io.ByteArrayOutputStream // 💡 ByteArrayOutputStream Import

// 🚨 클래스 정의를 하나로 통합합니다.
class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

    // 1. 상태 관리를 위한 변수 정의
    private var productStatus: String = "AVAILABLE"
    private var selectedPriceUnit: String = ""
    private var rentalPriceString: String = ""
    private val selectedImageUris = mutableListOf<Uri>()
    private val MAX_IMAGE_COUNT = 4

    // 💡 [추가] 설정된 주소 및 좌표 값
    private var selectedAddress: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null


    // Activity Result Launcher 정의
    // 🚨 [핵심 수정] GetMultipleContents로 변경하여 다중 선택 지원
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        uris?.let {
            // 기존 이미지 개수 + 새로 선택된 이미지 개수가 MAX_IMAGE_COUNT를 초과하는지 확인
            val newUris = it.take(MAX_IMAGE_COUNT - selectedImageUris.size)
            selectedImageUris.addAll(newUris)

            if (it.size > newUris.size) {
                Toast.makeText(requireContext(), "최대 ${MAX_IMAGE_COUNT}장까지만 선택 가능합니다.", Toast.LENGTH_SHORT).show()
            }

            // 💡 [UI 업데이트 필요] 바인딩의 텍스트 뷰 업데이트 (XML의 0/10 부분을 가정)
            // binding.textViewImageCount.text = "${selectedImageUris.size}/${MAX_IMAGE_COUNT}"
            Toast.makeText(requireContext(), "사진 첨부 완료! (총 ${selectedImageUris.size}장)", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 💡 [핵심 추가] SettingMapActivity로부터 결과를 받는 Launcher
     */
    private val mapResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            selectedAddress = data?.getStringExtra("FINAL_ADDRESS")
            selectedLatitude = data?.getDoubleExtra("FINAL_LATITUDE", 0.0)
            selectedLongitude = data?.getDoubleExtra("FINAL_LONGITUDE", 0.0)

            if (selectedAddress != null && selectedAddress!!.isNotEmpty()) {
                binding.textViewAddress.text = selectedAddress
                Log.d("MAP_RESULT", "주소 수신 성공: $selectedAddress / $selectedLatitude, $selectedLongitude")
                Toast.makeText(requireContext(), "거래 지역 설정 완료", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "거래 지역 설정 실패", Toast.LENGTH_SHORT).show()
            }
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
            // 💡 [핵심 수정] ActivityResultLauncher를 사용하도록 변경
            val currentUserId = AuthTokenManager.getUserId()
            val token = AuthTokenManager.getToken()

            if (currentUserId == null || token.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(requireContext(), SettingMapActivity::class.java).apply {
                putExtra("USER_ID", currentUserId)
                putExtra("SERVICE_TOKEN", token)
            }
            mapResultLauncher.launch(intent) // 💡 [수정] Launcher로 실행
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
            // 💡 [개선] DTO에 단위 필드를 추가해야 하지만, 현재는 UI에서만 조합하여 표시
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
        // 💡 [수정] 이미 선택된 개수를 확인하여, MAX_IMAGE_COUNT에 도달하지 않았을 때만 갤러리 런처를 실행합니다.
        if (selectedImageUris.size < MAX_IMAGE_COUNT) {
            // launch("image/*")는 GetMultipleContents()에 의해 다중 선택이 가능합니다.
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(requireContext(), "최대 ${MAX_IMAGE_COUNT}장의 이미지만 등록할 수 있습니다.", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * ✅ [핵심 수정] Base64 변환 로직을 백그라운드에서 처리하여 DTO 전송
     */
    private fun submitPost() {
        // 1. 데이터 수집 및 검증 (기존 로직 유지)
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val depositText = binding.editTextDeposit.text.toString().trim()

        // 🚨 [핵심 검증] 주소/좌표 유효성 검사
        if (selectedAddress.isNullOrEmpty() || selectedLatitude == null || selectedLongitude == null) {
            Toast.makeText(requireContext(), "거래 희망 지역을 설정해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        if (title.isEmpty() || category.isEmpty() || rentalPriceString.isEmpty()) {
            Toast.makeText(requireContext(), "필수 정보를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🚨 [이미지 검증] 최소 1개 이미지가 있어야 함
        if (selectedImageUris.isEmpty()) {
            Toast.makeText(requireContext(), "최소 1개 이상의 이미지를 등록해야 합니다.", Toast.LENGTH_LONG).show()
            return
        }

        // UI를 잠그고 백그라운드 작업 시작
        binding.completeButton.isEnabled = false

        lifecycleScope.launch {

            val base64List = withContext(Dispatchers.IO) {
                // 💡 [핵심] 모든 URI를 순회하며 Base64 문자열로 변환 (I/O 작업)
                selectedImageUris.mapNotNull { uri ->
                    // 🚨 [수정] 압축 로직을 추가한 변환 함수 호출
                    convertUriToBase64(uri, 50)
                }
            }

            // Base64 변환 중 오류가 발생했거나 리스트가 비어있으면 UI 복구
            if (base64List.isEmpty()) {
                Toast.makeText(requireContext(), "이미지 변환에 실패했습니다. (지원되지 않는 형식)", Toast.LENGTH_LONG).show()
                binding.completeButton.isEnabled = true
                return@launch
            }

            // 2. 데이터 변환 및 DTO 생성 (Base64 리스트 사용)
            val price = rentalPriceString.toIntOrNull() ?: 0
            val deposit: Int? = depositText.toIntOrNull()

            // 💡 [필드 추가] selectedPriceUnit을 DTO에 추가해야 하나, 현재 DTO는 단위를 위한 필드가 없습니다.
            // 임시로 description에 포함하거나, 서버 DTO를 수정해야 합니다. (여기서는 description에 임시 포함)
            val finalDescription = "$description (가격 단위: $selectedPriceUnit)"

            val request = ProductCreateRequest(
                title = title,
                price = price,
                description = finalDescription, // 💡 [임시] 가격 단위 포함
                category = category,
                status = productStatus,
                deposit = deposit,
                imageUrls = base64List, // 💡 Base64 리스트 전송
                address = selectedAddress!!
            )

            // 4. Retrofit 서버 통신 실행
            RetrofitClient.getApiService().createProduct(request)
                .enqueue(object : Callback<MsgEntity> {
                    override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                        binding.completeButton.isEnabled = true
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "게시글 등록 성공!", Toast.LENGTH_LONG).show()
                            parentFragmentManager.popBackStack()
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e("POST_API", "등록 실패: ${response.code()}, 메시지: $errorBody")
                            Toast.makeText(requireContext(), "등록 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                        binding.completeButton.isEnabled = true
                        Log.e("POST_API", "서버 통신 오류", t)
                        Toast.makeText(requireContext(), "서버 연결 오류 발생", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    /**
     * 💡 [추가] URI를 Base64 문자열로 변환하는 유틸리티 함수 (압축 로직 포함)
     * @param quality 압축 품질 (0-100)
     */
    private fun convertUriToBase64(uri: Uri, quality: Int): String? {
        return try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()

                // 🚨 [핵심] JPEG 형식으로 압축 (Quality 0~100)
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val compressedBytes = outputStream.toByteArray()
                outputStream.close()

                // Base64 인코딩 시 줄바꿈(NO_WRAP) 없이 처리
                return Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            }
            null
        } catch (e: Exception) {
            Log.e("BASE64_CONV", "URI to Base64 failed for $uri", e)
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}