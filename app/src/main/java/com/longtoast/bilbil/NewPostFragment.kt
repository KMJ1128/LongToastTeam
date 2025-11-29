package com.longtoast.bilbil

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
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
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream

object PriceUnitMapper {
    fun toInt(label: String): Int = when (label) {
        "일" -> 1; "월" -> 2; "시간" -> 3; else -> 1
    }
    fun toLabel(unit: Int): String = when (unit) {
        1 -> "일"; 2 -> "월"; 3 -> "시간"; else -> "일"
    }
}

class NewPostFragment : Fragment(), PriceUnitDialogFragment.PriceUnitListener {

    private var _binding: ActivityNewPostFragmentBinding? = null
    private val binding get() = _binding!!

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

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris != null) {
                val remain = MAX_IMAGE_COUNT - selectedImageUris.size
                selectedImageUris.addAll(uris.take(remain))
                updateImageUI()
            }
        }

    private val mapResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                selectedAddress = data?.getStringExtra("FINAL_ADDRESS")
                selectedLatitude = data?.getDoubleExtra("FINAL_LATITUDE", 0.0)
                selectedLongitude = data?.getDoubleExtra("FINAL_LONGITUDE", 0.0)
                binding.textViewAddress.text = selectedAddress ?: ""
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityNewPostFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImageRecyclerView()

        arguments?.getString(ARG_PRODUCT_JSON)?.let {
            editingProduct = Gson().fromJson(it, ProductDTO::class.java)
        }
        editingProduct?.let { prefillFields(it) }

        updatePriceTextView()

        binding.completeButton.setOnClickListener { submitPost() }
        binding.closeButton.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.layoutCameraArea.setOnClickListener {
            if (selectedImageUris.size < MAX_IMAGE_COUNT)
                galleryLauncher.launch("image/*")
        }

        binding.textViewAddress.setOnClickListener {
            val userId = AuthTokenManager.getUserId()
            val token = AuthTokenManager.getToken()
            if (userId != null && !token.isNullOrEmpty()) {
                val intent = Intent(requireContext(), SettingMapActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("SERVICE_TOKEN", token)
                mapResultLauncher.launch(intent)
            }
        }

        binding.editTextPrice.setOnClickListener { showPriceUnitSelectionDialog() }

        updateImageUI()
    }

    private fun setupImageRecyclerView() {
        imageAdapter = SelectedImageAdapter(selectedImageUris) { position ->
            selectedImageUris.removeAt(position)
            updateImageUI()
        }
        binding.recyclerSelectedImages.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerSelectedImages.adapter = imageAdapter
    }

    private fun updateImageUI() {
        imageAdapter.notifyDataSetChanged()
        binding.recyclerSelectedImages.visibility =
            if (selectedImageUris.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun submitPost() {
        val title = binding.editTextTitle.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()
        val category = binding.editTextCategory.text.toString().trim()
        val depositText = binding.editTextDeposit.text.toString().trim()

        if (title.isEmpty() || category.isEmpty() || rentalPriceString.isEmpty()) {
            Toast.makeText(requireContext(), "필수 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedAddress.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "주소를 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        lifecycleScope.launch {

            val imageParts = withContext(Dispatchers.IO) {
                convertImagesToMultipart(selectedImageUris)
            }

            val req = ProductCreateRequest(
                title = title,
                price = rentalPriceString.toInt(),
                price_unit = PriceUnitMapper.toInt(selectedPriceUnit),
                description = description,
                category = category,
                status = productStatus,
                deposit = depositText.toIntOrNull(),
                imageUrls = emptyList(),
                address = selectedAddress!!,
                latitude = selectedLatitude ?: 0.0,
                longitude = selectedLongitude ?: 0.0
            )

            editingProduct?.let { product ->

                RetrofitClient.getApiService()
                    .updateProduct(product.id, req)
                    .enqueue(object : Callback<MsgEntity> {
                        override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                            setLoadingState(false)
                            if (response.isSuccessful) {
                                Toast.makeText(requireContext(), "수정 완료!", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            } else Toast.makeText(requireContext(), "수정 실패", Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                            setLoadingState(false)
                            Toast.makeText(requireContext(), "서버 오류", Toast.LENGTH_SHORT).show()
                        }
                    })

            } ?: run {

                val json = Gson().toJson(req)
                val body = json.toRequestBody("application/json".toMediaType())

                val productPart = MultipartBody.Part.createFormData("product", null, body)

                RetrofitClient.getApiService()
                    .createProduct(productPart, imageParts)
                    .enqueue(object : Callback<MsgEntity> {
                        override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                            setLoadingState(false)
                            if (response.isSuccessful) {
                                Toast.makeText(requireContext(), "등록 성공!", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            } else Toast.makeText(requireContext(), "등록 실패", Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                            setLoadingState(false)
                            Toast.makeText(requireContext(), "서버 오류", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.progressLoader.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.completeButton.isEnabled = !isLoading
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

    private fun prefillFields(product: ProductDTO) {
        binding.editTextTitle.setText(product.title)
        binding.editTextDescription.setText(product.description ?: "")
        binding.editTextCategory.setText(product.category ?: "")
        binding.editTextDeposit.setText(product.deposit?.toString() ?: "")
        binding.textViewAddress.text = product.address ?: ""

        rentalPriceString = product.price.toString()
        selectedPriceUnit = PriceUnitMapper.toLabel(product.price_unit)
        updatePriceTextView()
    }

    // 🔥🔥🔥 EXIF 회전 보정 + JPEG 압축 버전
    private fun convertImagesToMultipart(uris: List<Uri>): List<MultipartBody.Part> {
        val result = mutableListOf<MultipartBody.Part>()

        for ((index, uri) in uris.withIndex()) {
            try {
                val input = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)
                input?.close()

                // EXIF 불러오기
                val exifInput = requireContext().contentResolver.openInputStream(uri)
                val exif = ExifInterface(exifInput!!)
                exifInput.close()

                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val rotated = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }

                val outputStream = ByteArrayOutputStream()
                rotated.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

                val bytes = outputStream.toByteArray()
                val reqBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())

                val part = MultipartBody.Part.createFormData(
                    "images", "image_$index.jpg", reqBody
                )
                result.add(part)

            } catch (e: Exception) {
                Log.e("IMAGE_CONVERT", "이미지 처리 실패", e)
            }
        }
        return result
    }

    private fun rotateBitmap(src: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    companion object {
        private const val ARG_PRODUCT_JSON = "ARG_PRODUCT_JSON"
        fun newInstance(product: ProductDTO) = NewPostFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PRODUCT_JSON, Gson().toJson(product))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
