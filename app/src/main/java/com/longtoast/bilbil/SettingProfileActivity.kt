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
import com.longtoast.bilbil.api.RetrofitClient // 💡 RetrofitClient Import
import com.longtoast.bilbil.dto.MemberDTO // 💡 MemberDTO Import
import com.longtoast.bilbil.dto.MsgEntity // 💡 MsgEntity Import
import java.io.File
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response // 💡 Retrofit Response

class SettingProfileActivity : AppCompatActivity() {

    private lateinit var imageProfile: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var editNickname: EditText
    private lateinit var textLocationInfo: TextView
    private lateinit var buttonComplete: Button

    private var profileImageUri: Uri? = null
    private var profileBitmap: Bitmap? = null

    // Intent로 받은 데이터
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var address: String = ""
    private var userNickname: String = ""

    // 💡 [핵심] MainActivity -> SettingMapActivity를 거쳐 전달받은 JWT 정보
    private var serviceToken: String? = null
    private var userId: Int = 0

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

        // Intent로 받은 데이터 가져오기
        getIntentData()

        // View 초기화
        initViews()

        // 데이터 표시
        displayData()

        // 리스너 설정
        setupListeners()
    }

    private fun getIntentData() {
        latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        longitude = intent.getDoubleExtra("LONGITUDE", 0.0)
        address = intent.getStringExtra("ADDRESS") ?: ""
        userNickname = intent.getStringExtra("USER_NICKNAME") ?: ""

        // 💡 [핵심 추가] SettingMapActivity에서 전달받은 JWT와 User ID
        serviceToken = intent.getStringExtra("SERVICE_TOKEN")
        userId = intent.getIntExtra("USER_ID", 0)


        Log.d("SettingProfile", "받은 데이터 - 위도: $latitude, 경도: $longitude")
        Log.d("SettingProfile", "주소: $address, 닉네임: $userNickname")
        Log.d("SettingProfile", "인증 정보 - USER_ID: $userId, SERVICE_TOKEN: ${serviceToken?.substring(0, 10)}...")
    }

    private fun initViews() {
        imageProfile = findViewById(R.id.image_profile)
        fabChangePhoto = findViewById(R.id.fab_change_photo)
        editNickname = findViewById(R.id.edit_nickname)
        textLocationInfo = findViewById(R.id.text_location_info)
        buttonComplete = findViewById(R.id.button_complete)
    }

    private fun displayData() {
        // 닉네임 표시 (서버에서 받은 닉네임 또는 빈 문자열)
        editNickname.setText(userNickname)

        // 주소 표시
        if (address.isNotEmpty()) {
            textLocationInfo.text = address
        } else {
            textLocationInfo.text = "위도: $latitude, 경도: $longitude"
        }
    }

    private fun setupListeners() {
        // 프로필 사진 변경 버튼
        fabChangePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        // 완료 버튼
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
        // 카메라 권한 확인
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            // 권한 요청
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
            // 임시 파일 생성
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
            // URI를 Bitmap으로 변환
            val inputStream = contentResolver.openInputStream(uri)
            profileBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // ImageView에 표시
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
     * 완료 버튼 클릭 시, 서버 통신 후 홈 화면으로 이동하며 스택 정리
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

        // 1. DTO 생성 (백엔드 MemberDTO의 8개 필드를 모두 채워서 보냅니다.)
        val updateRequest = MemberDTO(
            id = userId,
            nickname = nickname,
            address = address,
            locationLatitude = latitude,
            locationLongitude = longitude,
            creditScore = 720, // 더미 값 (MemberDTO의 모든 필드 수를 맞추기 위함)
            profileImageUrl = null, // 더미 값
            createdAt = null // 더미 값
        )

        // 2. 🔑 [핵심 추가] API 호출: 프로필 업데이트 (DB 저장)
        RetrofitClient.getApiService().updateProfile(updateRequest)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        // 3. 성공 시, 토큰 저장 및 홈 화면 이동
                        AuthTokenManager.saveToken(serviceToken!!)
                        AuthTokenManager.saveUserId(userId)

                        Log.d("PROFILE_COMPLETE", "✅ 프로필 업데이트 및 JWT 저장 완료.")
                        Toast.makeText(this@SettingProfileActivity, "프로필 설정 및 저장 완료!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@SettingProfileActivity, HomeHostActivity::class.java)
                        startActivity(intent)
                        finishAffinity()
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("PROFILE_API", "프로필 업데이트 실패: ${response.code()}, 메시지: $errorBody")
                        Toast.makeText(this@SettingProfileActivity, "닉네임 등록 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Log.e("PROFILE_API", "서버 통신 오류", t)
                    Toast.makeText(this@SettingProfileActivity, "서버 연결 오류", Toast.LENGTH_LONG).show()
                }
            })
    }
}