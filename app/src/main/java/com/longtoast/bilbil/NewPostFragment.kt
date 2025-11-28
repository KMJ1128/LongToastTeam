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
import androidx.recyclerview.widget.LinearLayoutManager
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
    fun toInt(label: String): Int = when (label) { "일" -> 1; "월" -> 2; "시간" -> 3; else -> 1 }
    fun toLabel(unit: Int): String = when (unit) { 1 -> "일"; 2 -> "월"; 3 -> "시간"; else -> "일" }
}

class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

    // ✅ 최대 이미지 개수 4개
    private val MAX_IMAGE_COUNT = 4
    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var imageAdapter: SelectedImageAdapter

    private var productStatus: String = "AVAILABLE"
    private var selectedPriceUnit: String = ""
    private var rentalPriceString: String = ""
    private var editingProduct: ProductDTO? = null

    private var selectedAddress: String? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    // 갤러리 런처
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris != null) {
                // 개수 제한 체크
                val currentCount = selectedImageUris.size
                val newCount = uris.size
                val available = MAX_IMAGE_COUNT - currentCount

                if (newCount > available) {
                    Toast.makeText(requireContext(), "사진은 최대 4장까지 선택 가능합니다.", Toast.LENGTH_SHORT).show()
                }

                // 가능한 만큼만 추가
                selectedImageUris.addAll(uris.take(available))
                updateImageUI()
            }
        }

    // 지도 런처
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityNewPostFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ 리사이클러뷰 설정
        setupImageRecyclerView()

        // 수정 모드 데이터 채우기
        arguments?.getString(ARG_PRODUCT_JSON)?.let { json ->
            editingProduct = Gson().fromJson(json, ProductDTO::class.java)
        }
        editingProduct?.let { prefillFields(it) }
        updatePriceTextView()

        // 리스너 설정
        binding.completeButton.setOnClickListener { submitPost() }
        binding.closeButton.setOnClickListener { parentFragmentManager.popBackStack() }

        // ✅ 카메라 영역 클릭 시 갤러리 열기
        binding.layoutCameraArea.setOnClickListener {
            if (selectedImageUris.size < MAX_IMAGE_COUNT) {
                galleryLauncher.launch("image/*")
            } else {
                Toast.makeText(requireContext(), "사진을 더 이상 추가할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.textViewAddress.setOnClickListener {
            val userId = AuthTokenManager.getUserId()
            val token = AuthTokenManager.getToken()
            if (userId != null && !token.isNullOrEmpty()) {
                val intent = Intent(requireContext(), SettingMapActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra("SERVICE_TOKEN", token)
                }
                mapResultLauncher.launch(intent)
            } else {
                Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.editTextPrice.setOnClickListener { showPriceUnitSelectionDialog() }

        // 초기 UI 업데이트
        updateImageUI()
    }

    private fun setupImageRecyclerView() {
        imageAdapter = SelectedImageAdapter(selectedImageUris) { position ->
            // 삭제 버튼 클릭 시
            selectedImageUris.removeAt(position)
            updateImageUI()
        }
        binding.recyclerSelectedImages.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerSelectedImages.adapter = imageAdapter
    }

    // ✅ [수정됨] 사진 유무에 따라 리스트 보이기/숨기기 (layoutPlaceholder 제거)
    private fun updateImageUI() {
        imageAdapter.notifyDataSetChanged()

        // 사진이 있으면 리스트를 보여주고, 없으면 숨김
        if (selectedImageUris.isEmpty()) {
            binding.recyclerSelectedImages.visibility = View.GONE
        } else {
            binding.recyclerSelectedImages.visibility = View.VISIBLE
        }
    }

    // ------------------------------------------------------------------------
    // 작성 완료 로직 (중복 방지 포함)
    // ------------------------------------------------------------------------
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

        // 🟢 로딩 시작
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
                        override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                            setLoadingState(false) // 🟢 로딩 종료
                            if (response.isSuccessful) {
                                Toast.makeText(requireContext(), "수정되었습니다.", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            } else {
                                Toast.makeText(requireContext(), "수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                            setLoadingState(false) // 🟢 로딩 종료
                            Log.e("POST_API", "서버 오류", t)
                            Toast.makeText(requireContext(), "서버 통신 오류", Toast.LENGTH_LONG).show()
                        }
                    })
            } ?: run {
                val productRequestBody: RequestBody =
                    Gson().toJson(requestObj)
                        .toRequestBody("application/json; charset=utf-8".toMediaType())

                RetrofitClient.getApiService()
                    .createProduct(productRequestBody, imageParts)
                    .enqueue(object : Callback<MsgEntity> {
                        override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                            setLoadingState(false) // 🟢 로딩 종료
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
                            setLoadingState(false) // 🟢 로딩 종료
                            Log.e("POST_API", "서버 오류", t)
                            Toast.makeText(requireContext(), "서버 통신 오류", Toast.LENGTH_LONG).show()
                        }
                    })
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.progressLoader.visibility = View.VISIBLE
            binding.completeButton.isEnabled = false
        } else {
            binding.progressLoader.visibility = View.GONE
            binding.completeButton.isEnabled = true
        }
    }

    override fun onPriceUnitSelected(price: String, unit: String) {
        rentalPriceString = price
        selectedPriceUnit = unit
        updatePriceTextView()
    }

    private fun updatePriceTextView() {
        if (rentalPriceString.isEmpty()) binding.editTextPrice.hint = "₩ 대여 가격 (단위 선택)"
        else binding.editTextPrice.text = "₩ $rentalPriceString / $selectedPriceUnit"
    }

    private fun showPriceUnitSelectionDialog() {
        PriceUnitDialogFragment().show(childFragmentManager, "PriceUnitDialog")
    }

    private fun prefillFields(product: ProductDTO) {
        binding.editTextTitle.setText(product.title)
        binding.editTextDescription.setText(product.description ?: "")
        binding.editTextCategory.setText(product.category ?: "")
        binding.editTextDeposit.setText(product.deposit?.toString() ?: "")
        binding.textViewAddress.text = product.address ?: "주소 미지정"

        rentalPriceString = product.price.toString()
        selectedPriceUnit = PriceUnitMapper.toLabel(product.price_unit)
        updatePriceTextView()
        binding.completeButton.text = "상품 수정"
    }

    private fun convertImagesToMultipart(uris: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        for ((index, uri) in uris.withIndex()) {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes() ?: continue
            inputStream.close()
            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            parts.add(MultipartBody.Part.createFormData("images", "image_$index.jpg", requestBody))
        }
        return parts
    }

    companion object {
        private const val ARG_PRODUCT_JSON = "ARG_PRODUCT_JSON"
        fun newInstance(product: ProductDTO) = NewPostFragment().apply {
            arguments = Bundle().apply { putString(ARG_PRODUCT_JSON, Gson().toJson(product)) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}