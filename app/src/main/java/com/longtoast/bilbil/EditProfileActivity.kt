package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imageProfile: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    //private lateinit var textNicknameValue: TextView
    private lateinit var editNickname: EditText
    private lateinit var textLocationInfo: TextView
    private lateinit var buttonChangeLocation: Button
    private lateinit var buttonSave: Button

    private var profileImageUri: Uri? = null
    private var profileBitmap: Bitmap? = null
    private var currentImageUrl: String? = null

    private var currentNickname: String = ""
    private var currentAddress: String = ""
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentUserId: Int = 0
    private var currentUsername: String? = null
    private var currentCreditScore: Int = 720

    private var hasImageChanged = false
    private var hasLocationChanged = false

    private val CAMERA_PERMISSION_CODE = 100

    // 갤러리에서 이미지 선택
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleImageResult(it)
            hasImageChanged = true
        }
    }

    // 카메라로 사진 촬영
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            profileImageUri?.let {
                handleImageResult(it)
                hasImageChanged = true
            }
        }
    }

    // 지역 선택 결과
    private val regionSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

            val address = result.data?.getStringExtra("FINAL_ADDRESS") ?: return@registerForActivityResult
            val latitude = result.data?.getDoubleExtra("FINAL_LATITUDE", 0.0) ?: 0.0
            val longitude = result.data?.getDoubleExtra("FINAL_LONGITUDE", 0.0) ?: 0.0

            currentAddress = address
            currentLatitude = latitude
            currentLongitude = longitude
            textLocationInfo.text = address
            hasLocationChanged = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        initViews()
        loadCurrentProfile()
        setupListeners()
    }

    private fun initViews() {
        imageProfile = findViewById(R.id.image_profile)
        fabChangePhoto = findViewById(R.id.fab_change_photo)
        editNickname = findViewById(R.id.edit_nickname)  // 🆕 변경
        textLocationInfo = findViewById(R.id.text_location_info)
        buttonChangeLocation = findViewById(R.id.button_change_location)
        buttonSave = findViewById(R.id.button_save)
    }

    private fun loadCurrentProfile() {
        RetrofitClient.getApiService().getMyInfo()
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (!response.isSuccessful) {
                        Toast.makeText(
                            this@EditProfileActivity,
                            "프로필 정보를 불러올 수 없습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return
                    }

                    val raw = response.body()?.data ?: return

                    try {
                        val gson = Gson()
                        val type = object : TypeToken<MemberDTO>() {}.type
                        val member: MemberDTO = gson.fromJson(gson.toJson(raw), type)

                        // UI 업데이트 부분만 수정
                        currentUserId = member.id
                        currentNickname = member.nickname ?: ""
                        currentUsername = member.username
                        currentAddress = member.address ?: "위치 미설정"
                        currentLatitude = member.locationLatitude ?: 0.0
                        currentLongitude = member.locationLongitude ?: 0.0
                        currentCreditScore = member.creditScore ?: 720
                        currentImageUrl = member.profileImageUrl

                        // UI 업데이트
                        editNickname.setText(currentNickname)  // 🆕 변경 (textNicknameValue → editNickname)
                        textLocationInfo.text = currentAddress

                        // 프로필 이미지 로드
                        if (!currentImageUrl.isNullOrEmpty()) {
                            val fullUrl = ImageUrlUtils.resolve(currentImageUrl!!)
                            Glide.with(this@EditProfileActivity)
                                .load(fullUrl)
                                .circleCrop()
                                .placeholder(R.drawable.no_profile)
                                .error(R.drawable.no_profile)
                                .into(imageProfile)
                        }

                    } catch (e: Exception) {
                        Log.e("EDIT_PROFILE", "MemberDTO 파싱 오류", e)
                        Toast.makeText(
                            this@EditProfileActivity,
                            "프로필 정보를 불러올 수 없습니다",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("EDIT_PROFILE", "네트워크 오류", t)
                    Toast.makeText(
                        this@EditProfileActivity,
                        "네트워크 오류가 발생했습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            })
    }

    private fun setupListeners() {
        fabChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        buttonChangeLocation.setOnClickListener {
            val intent = Intent(this, RegionSelectionActivity::class.java).apply {
                putExtra("USER_ID", currentUserId)
                putExtra("SERVICE_TOKEN", AuthTokenManager.getToken())
                putExtra("USER_NICKNAME", currentNickname)
                putExtra("IS_PROFILE_EDIT", true)  // 🆕 추가: 프로필 수정 모드 표시
            }
            regionSelectionLauncher.launch(intent)
        }

        buttonSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("갤러리에서 선택", "카메라로 촬영", "취소")

        AlertDialog.Builder(this)
            .setTitle("프로필 사진 변경")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openGallery()
                    1 -> openCamera()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File.createTempFile(
                "profile_",
                ".jpg",
                cacheDir
            )

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )

            profileImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "카메라 실행 오류", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImageResult(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            profileBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            imageProfile.setImageBitmap(profileBitmap)

            Log.d("IMAGE", "프로필 이미지 설정 완료")
            Toast.makeText(this, "프로필 사진이 설정되었습니다", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("IMAGE", "이미지 로드 오류", e)
            Toast.makeText(this, "이미지를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfile() {
        // 🆕 닉네임 유효성 검사 추가
        val newNickname = editNickname.text.toString().trim()

        if (newNickname.isEmpty() || newNickname.length < 2) {
            Toast.makeText(this, "닉네임은 2자 이상이어야 합니다", Toast.LENGTH_SHORT).show()
            editNickname.requestFocus()
            return
        }

        // 🆕 닉네임 변경 여부 체크
        val hasNicknameChanged = newNickname != currentNickname

        if (!hasImageChanged && !hasLocationChanged && !hasNicknameChanged) {
            Toast.makeText(this, "변경된 내용이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        buttonSave.isEnabled = false

        // 🆕 MemberDTO 생성 시 새로운 닉네임 사용
        val updateRequest = MemberDTO(
            id = currentUserId,
            nickname = newNickname,  // 🆕 변경
            username = currentUsername,
            address = currentAddress,
            locationLatitude = currentLatitude,
            locationLongitude = currentLongitude,
            creditScore = currentCreditScore,
            profileImageUrl = null,
            createdAt = null
        )

        val gson = Gson()
        val memberJson = gson.toJson(updateRequest)

        val memberRequestBody = memberJson
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // 프로필 이미지 Multipart 변환
        var imagePart: MultipartBody.Part? = null

        if (hasImageChanged && profileBitmap != null) {
            try {
                val file = File(cacheDir, "profile_upload.jpg")

                val fos = FileOutputStream(file)
                profileBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.flush()
                fos.close()

                val reqFile = file.asRequestBody("image/jpeg".toMediaType())
                imagePart = MultipartBody.Part.createFormData(
                    "profileImage",
                    file.name,
                    reqFile
                )

                Log.d("PROFILE_API", "이미지 multipart 변환 성공 → ${file.name}")

            } catch (e: Exception) {
                Log.e("PROFILE_API", "이미지 변환 실패", e)
            }
        }

        // API 호출
        RetrofitClient.getApiService().updateProfile(
            memberRequestBody,
            imagePart
        ).enqueue(object : Callback<MsgEntity> {

            // API 응답 성공 후 SharedPreferences 업데이트 부분에 추가
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                buttonSave.isEnabled = true

                if (!response.isSuccessful) {
                    Log.e("PROFILE_API", "프로필 업데이트 실패: ${response.code()}, ${response.errorBody()?.string()}")
                    Toast.makeText(
                        this@EditProfileActivity,
                        "프로필 저장 실패: ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }

                Toast.makeText(
                    this@EditProfileActivity,
                    "프로필이 성공적으로 업데이트되었습니다!",
                    Toast.LENGTH_SHORT
                ).show()

                // 🆕 SharedPreferences 업데이트 (닉네임 추가)
                AuthTokenManager.saveNickname(newNickname)
                AuthTokenManager.saveAddress(currentAddress)

                // 결과 반환 및 종료
                setResult(RESULT_OK)
                finish()
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                buttonSave.isEnabled = true
                Log.e("PROFILE_API", "서버 통신 오류", t)
                Toast.makeText(
                    this@EditProfileActivity,
                    "서버 연결 오류",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }
}