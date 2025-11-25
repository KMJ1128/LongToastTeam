package com.longtoast.bilbil

import android.app.Activity
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
import com.google.gson.Gson
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityNewPostFragmentBinding
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.InputStream

class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

    private var productStatus: String = "AVAILABLE"
    private var selectedPriceUnit: String = ""
    private var rentalPriceString: String = ""

    private val selectedImageUris = mutableListOf<Uri>()
    private val MAX_IMAGE_COUNT = 4

    private var selectedAddress: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris != null) {
                selectedImageUris.clear()
                selectedImageUris.addAll(uris.take(MAX_IMAGE_COUNT))
                Toast.makeText(
                    requireContext(),
                    "이미지 선택됨 (${selectedImageUris.size}장)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val mapResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                selectedAddress = data?.getStringExtra("FINAL_ADDRESS")
                selectedLatitude = data?.getDoubleExtra("FINAL_LATITUDE", 0.0)
                selectedLongitude = data?.getDoubleExtra("FINAL_LONGITUDE", 0.0)

                binding.textViewAddress.text = selectedAddress ?: "주소 선택 실패"
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityNewPostFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updatePriceTextView()

        // 등록 버튼
        binding.completeButton.setOnClickListener { submitPost() }

        // 닫기 버튼
        binding.closeButton.setOnClickListener { parentFragmentManager.popBackStack() }

        // 이미지 선택
        binding.layoutCameraArea.setOnClickListener { openGalleryForImage() }

        // 주소 선택
        binding.textViewAddress.setOnClickListener {
            val userId = AuthTokenManager.getUserId()
            val token = AuthTokenManager.getToken()

            if (userId == null || token.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(requireContext(), SettingMapActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("SERVICE_TOKEN", token)
            }
            mapResultLauncher.launch(intent)
        }

        // 가격 선택 팝업
        binding.editTextPrice.setOnClickListener { showPriceUnitSelectionDialog() }

        setupStatusToggleGroup()
    }

    override fun onPriceUnitSelected(price: String, unit: String) {
        rentalPriceString = price
        selectedPriceUnit = unit
        updatePriceTextView()
    }

    private fun updatePriceTextView() {
        if (rentalPriceString.isEmpty()) {
            binding.editTextPrice.hint = "₩ 대여 가격 (단위 선택)"
        } else {
            binding.editTextPrice.setText("₩ $rentalPriceString / $selectedPriceUnit")
        }
    }

    private fun showPriceUnitSelectionDialog() {
        PriceUnitDialogFragment().show(childFragmentManager, "PriceUnitDialog")
    }

    private fun setupStatusToggleGroup() {
        binding.toggleStatusGroup.check(R.id.button_rent_available)

        binding.toggleStatusGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                productStatus = if (id == R.id.button_rent_available) {
                    "AVAILABLE"
                } else {
                    "UNAVAILABLE"
                }
            }
        }
    }

    private fun openGalleryForImage() {
        galleryLauncher.launch("image/*")
    }

    private fun submitPost() {
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val depositText = binding.editTextDeposit.text.toString().trim()

        // Validation
        if (selectedAddress.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "거래 지역을 설정해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty() || category.isEmpty() || rentalPriceString.isEmpty()) {
            Toast.makeText(requireContext(), "필수 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(requireContext(), "최소 1장의 이미지가 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.completeButton.isEnabled = false

        lifecycleScope.launch {

            // (3) 이미지 URI → MultipartBody.Part
            val imageParts = withContext(Dispatchers.IO) {
                convertImagesToMultipart(selectedImageUris)
            }

            val price = rentalPriceString.toIntOrNull() ?: 0
            val deposit = depositText.toIntOrNull()
            val finalDesc = "$description (가격 단위: $selectedPriceUnit)"

            val requestObj = ProductCreateRequest(
                title = title,
                price = price,
                description = finalDesc,
                category = category,
                status = productStatus,
                deposit = deposit,
                imageUrls = emptyList(),
                address = selectedAddress!!
            )

            // (2) JSON → RequestBody
            val productRequestBody: RequestBody =
                Gson().toJson(requestObj)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

            RetrofitClient.getApiService()
                .createProduct(productRequestBody, imageParts)
                .enqueue(object : Callback<MsgEntity> {
                    override fun onResponse(
                        call: Call<MsgEntity>,
                        response: Response<MsgEntity>
                    ) {
                        binding.completeButton.isEnabled = true

                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), "등록 성공!", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        } else {
                            val err = response.errorBody()?.string()
                            Log.e("POST_API", "실패: ${response.code()} | $err")
                            Toast.makeText(requireContext(), "등록 실패", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                        binding.completeButton.isEnabled = true
                        Log.e("POST_API", "서버 오류", t)
                        Toast.makeText(requireContext(), "서버 통신 오류", Toast.LENGTH_LONG).show()
                    }
                })
        }
    }

    // (3) 이미지 URI → Multipart 변환 함수
    private fun convertImagesToMultipart(uris: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()

        for ((index, uri) in uris.withIndex()) {
            val inputStream: InputStream? =
                requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: continue
            inputStream?.close()

            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData(
                name = "images",
                filename = "image_$index.jpg",
                body = requestBody
            )
            parts.add(multipart)
        }

        return parts
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
