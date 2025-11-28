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
import com.longtoast.bilbil.dto.ProductDTO
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

object PriceUnitMapper {
    fun toInt(label: String): Int = when (label) {
        "일" -> 1
        "월" -> 2
        "시간" -> 3
        else -> 1
    }

    fun toLabel(unit: Int): String = when (unit) {
        1 -> "일"
        2 -> "월"
        3 -> "시간"
        else -> "일"
    }
}

class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

    private var productStatus: String = "AVAILABLE"
    private var selectedPriceUnit: String = ""
    private var rentalPriceString: String = ""
    private var editingProduct: ProductDTO? = null

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

        arguments?.getString(ARG_PRODUCT_JSON)?.let { json ->
            editingProduct = Gson().fromJson(json, ProductDTO::class.java)
        }

        editingProduct?.let { prefillFields(it) }
        updatePriceTextView()

        binding.completeButton.setOnClickListener { submitPost() }
        binding.closeButton.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.layoutCameraArea.setOnClickListener { openGalleryForImage() }

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
            binding.editTextPrice.text = "₩ $rentalPriceString / $selectedPriceUnit"
        }
    }

    private fun showPriceUnitSelectionDialog() {
        PriceUnitDialogFragment().show(childFragmentManager, "PriceUnitDialog")
    }

    private fun setupStatusToggleGroup() {
        binding.toggleStatusGroup.check(R.id.button_rent_available)
        binding.toggleStatusGroup.addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                productStatus = if (id == R.id.button_rent_available) "AVAILABLE" else "UNAVAILABLE"
            }
        }
    }

    private fun openGalleryForImage() {
        galleryLauncher.launch("image/*")
    }

    // ✅ [수정] 작성/수정 완료 처리 함수 (중복 클릭 방지 적용)
    private fun submitPost() {
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val depositText = binding.editTextDeposit.text.toString().trim()

        if (selectedAddress.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "거래 지역을 설정해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (title.isEmpty() || category.isEmpty() || rentalPriceString.isEmpty() || selectedPriceUnit.isEmpty()) {
            Toast.makeText(requireContext(), "필수 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedImageUris.isEmpty() && editingProduct == null) {
            Toast.makeText(requireContext(), "최소 1장의 이미지가 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🟢 1. 로딩 시작 (버튼 비활성화 & 프로그레스바 표시)
        setLoadingState(true)

        lifecycleScope.launch {

            val imageParts = withContext(Dispatchers.IO) {
                convertImagesToMultipart(selectedImageUris)
            }

            val price = rentalPriceString.toIntOrNull() ?: 0
            val deposit = depositText.toIntOrNull()
            val priceUnitInt = PriceUnitMapper.toInt(selectedPriceUnit)

            val requestObj = ProductCreateRequest(
                title = title,
                price = price,
                price_unit = priceUnitInt,
                description = description,
                category = category,
                status = productStatus,
                deposit = deposit,
                imageUrls = emptyList(),
                address = selectedAddress!!,
                latitude = selectedLatitude ?: 0.0,
                longitude = selectedLongitude ?: 0.0
            )

            editingProduct?.let { product ->
                RetrofitClient.getApiService()
                    .updateProduct(product.id, requestObj)
                    .enqueue(object : Callback<MsgEntity> {
                        override fun onResponse(
                            call: Call<MsgEntity>,
                            response: Response<MsgEntity>
                        ) {
                            // 🟢 2. 응답 완료 (로딩 해제)
                            setLoadingState(false)

                            if (response.isSuccessful) {
                                Toast.makeText(requireContext(), "수정되었습니다.", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            } else {
                                Toast.makeText(requireContext(), "수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                            // 🟢 2. 통신 실패 (로딩 해제)
                            setLoadingState(false)
                            Log.e("POST_API", "서버 오류", t)
                            Toast.makeText(requireContext(), "서버 통신 오류", Toast.LENGTH_LONG).show()
                        }
                    })
            } ?: run {
                // 신규 등록
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
                            // 🟢 2. 응답 완료 (로딩 해제)
                            setLoadingState(false)

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
                            // 🟢 2. 통신 실패 (로딩 해제)
                            setLoadingState(false)
                            Log.e("POST_API", "서버 오류", t)
                            Toast.makeText(requireContext(), "서버 통신 오류", Toast.LENGTH_LONG).show()
                        }
                    })
            }
        }
    }

    // ✅ [추가] 로딩 상태 제어 (중복 클릭 방지용)
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            // 로딩 중: 프로그레스바 보이기, 버튼 끄기
            binding.progressLoader.visibility = View.VISIBLE
            binding.completeButton.isEnabled = false
        } else {
            // 로딩 끝: 프로그레스바 숨기기, 버튼 켜기
            binding.progressLoader.visibility = View.GONE
            binding.completeButton.isEnabled = true
        }
    }

    private fun prefillFields(product: ProductDTO) {
        binding.editTextTitle.setText(product.title)
        binding.editTextDescription.setText(product.description ?: "")
        binding.editTextCategory.setText(product.category ?: "")
        binding.editTextDeposit.setText(product.deposit?.toString() ?: "")
        binding.textViewAddress.text = product.address ?: product.tradeLocation ?: "주소 미지정"
        selectedAddress = product.address ?: product.tradeLocation
        selectedPriceUnit = "일"
        rentalPriceString = product.price.toString()
        selectedPriceUnit = PriceUnitMapper.toLabel(product.price_unit)

        productStatus = product.status ?: "AVAILABLE"
        if (productStatus == "AVAILABLE") binding.toggleStatusGroup.check(R.id.button_rent_available)
        else binding.toggleStatusGroup.check(R.id.button_rent_unavailable)

        binding.completeButton.text = "상품 수정"
        updatePriceTextView()
    }

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

    companion object {
        private const val ARG_PRODUCT_JSON = "ARG_PRODUCT_JSON"

        fun newInstance(product: ProductDTO): NewPostFragment {
            return NewPostFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRODUCT_JSON, Gson().toJson(product))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}