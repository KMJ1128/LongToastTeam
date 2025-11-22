// com.longtoast.bilbil.SettingProfileActivity.kt (전체)
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import java.io.File
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.longtoast.bilbil.api.RetrofitClient

class SettingProfileActivity : AppCompatActivity() {

    private lateinit var imageProfile: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var editNickname: EditText
    private lateinit var textLocationInfo: TextView
    private lateinit var buttonComplete: Button

    private var profileImageUri: Uri? = null
    private var profileBitmap: Bitmap? = null

    // Intent로 받은 데이터
    private var userNickname: String = ""

    private var serviceToken: String? = null
    private var userId: Int = 0

    private var userName: String? = null // 💡 [추가] MemberDTO.kt에 username 필드가 추가되었다는 가정
    private var pendingNickname: String = ""

    private val CAMERA_PERMISSION_CODE = 100

    // 갤러리에서 이미지 선택
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleImageResult(it)
        }
    }

    // 카메라로 사진 촬영
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            profileImageUri?.let {
                handleImageResult(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_profile)

        getIntentData()

        // 신규 회원의 초기 진입 시 토큰이 누락되지 않도록 안전하게 보관
        serviceToken?.let { AuthTokenManager.saveToken(it) }
        if (userId != 0) {
            AuthTokenManager.saveUserId(userId)
        }
        initViews()
        displayData()
        setupListeners()
    }

    private fun getIntentData() {
        userNickname = intent.getStringExtra("USER_NICKNAME") ?: ""

        serviceToken = intent.getStringExtra("SERVICE_TOKEN")
        userId = intent.getIntExtra("USER_ID", 0)
        userName = intent.getStringExtra("USER_NAME")

        Log.d(
            "SettingProfile",
            "인증 정보 - USER_ID: $userId, SERVICE_TOKEN: ${serviceToken?.substring(0, Math.min(serviceToken?.length ?: 0, 10))}..."
        )
    }

    private fun initViews() {
        imageProfile = findViewById(R.id.image_profile)
        fabChangePhoto = findViewById(R.id.fab_change_photo)
        editNickname = findViewById(R.id.edit_nickname)
        textLocationInfo = findViewById(R.id.text_location_info)
        buttonComplete = findViewById(R.id.button_complete)
    }

    private fun displayData() {
        editNickname.setText(userNickname)
        textLocationInfo.text = "지역 선택 단계에서 설정됩니다."
    }

    private fun setupListeners() {
        fabChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        buttonComplete.setOnClickListener {
            onCompleteButtonClicked()
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

    /**
     * 완료 버튼 클릭 시, 지역 선택 화면으로 이동한 뒤 위치까지 설정해 저장합니다.
     */
    private fun onCompleteButtonClicked() {
        val nickname = editNickname.text.toString().trim()

        // 닉네임 유효성 검사
        if (nickname.isEmpty() || nickname.length < 2) {
            Toast.makeText(this, "닉네임은 2자 이상이어야 합니다", Toast.LENGTH_SHORT).show()
            editNickname.requestFocus()
            return
        }

        if (serviceToken == null || userId == 0) {
            Log.e("PROFILE_COMPLETE", "🚨 JWT 또는 USER_ID 누락. 홈 이동 실패.")
            Toast.makeText(this, "인증 정보가 부족합니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finishAffinity()
            return
        }

        pendingNickname = nickname

        val intent = Intent(this, RegionSelectionActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("SERVICE_TOKEN", serviceToken)
            putExtra("USER_NICKNAME", nickname)
        }
        regionSelectionLauncher.launch(intent)
    }

    private val regionSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

            val address = result.data?.getStringExtra("FINAL_ADDRESS") ?: return@registerForActivityResult
            val latitude = result.data?.getDoubleExtra("FINAL_LATITUDE", 0.0) ?: 0.0
            val longitude = result.data?.getDoubleExtra("FINAL_LONGITUDE", 0.0) ?: 0.0

            textLocationInfo.text = address

            submitProfile(address, latitude, longitude)
        }

    private fun submitProfile(address: String, latitude: Double, longitude: Double) {
        // 1. DTO 생성
        val updateRequest = MemberDTO(
            id = userId,
            nickname = pendingNickname,
            username = userName, // 💡 [수정] username 필드 포함
            address = address,
            locationLatitude = latitude,
            locationLongitude = longitude,
            creditScore = 720,
            profileImageUrl = null,
            createdAt = null
        )

        // 2. 🔑 [핵심] API 호출 전에 AuthTokenManager에 토큰/ID를 저장합니다.
        // RetrofitClient의 Interceptor가 이 저장된 토큰을 즉시 사용합니다.
        serviceToken?.let { AuthTokenManager.saveToken(it) }
        AuthTokenManager.saveUserId(userId)
        Log.d("PROFILE_COMPLETE", "✅ JWT 및 User ID 저장 완료. API 호출 시작.")


        // 3. 🔑 [수정] RetrofitClient의 기본 ApiService를 사용합니다.
        RetrofitClient.getApiService().updateProfile(updateRequest)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Log.d("PROFILE_COMPLETE", "✅ 프로필 업데이트 성공 (200/201). 홈 이동.")
                        Toast.makeText(this@SettingProfileActivity, "프로필 설정 및 저장 완료!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@SettingProfileActivity, HomeHostActivity::class.java)
                        startActivity(intent)
                        finishAffinity()
                    } else if (response.code() == 403 || response.code() == 401) {
                        Log.e("PROFILE_API", "프로필 업데이트 실패: 인증 거부 (403/401). 토큰 무효화.")
                        Toast.makeText(this@SettingProfileActivity, "인증 오류. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                        AuthTokenManager.clearToken()
                        startActivity(Intent(this@SettingProfileActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                    else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("PROFILE_API", "프로필 업데이트 실패: ${response.code()}, 메시지: $errorBody")
                        Toast.makeText(this@SettingProfileActivity, "프로필 저장 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("PROFILE_API", "서버 통신 오류", t)
                    Toast.makeText(this@SettingProfileActivity, "서버 연결 오류", Toast.LENGTH_LONG).show()
                }
            })
    }
}