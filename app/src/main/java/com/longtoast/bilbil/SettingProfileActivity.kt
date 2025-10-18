package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.File
import java.io.IOException

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

        Log.d("SettingProfile", "받은 데이터 - 위도: $latitude, 경도: $longitude")
        Log.d("SettingProfile", "주소: $address, 닉네임: $userNickname")
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

    private fun onCompleteButtonClicked() {
        val nickname = editNickname.text.toString().trim()

        // 닉네임 유효성 검사
        if (nickname.isEmpty()) {
            Toast.makeText(this, "닉네임을 입력해주세요", Toast.LENGTH_SHORT).show()
            editNickname.requestFocus()
            return
        }

        if (nickname.length < 2) {
            Toast.makeText(this, "닉네임은 2자 이상이어야 합니다", Toast.LENGTH_SHORT).show()
            editNickname.requestFocus()
            return
        }

        Log.d("PROFILE_COMPLETE", "닉네임: $nickname")
        Log.d("PROFILE_COMPLETE", "위치: $address ($latitude, $longitude)")
        Log.d("PROFILE_COMPLETE", "프로필 이미지: ${if (profileBitmap != null) "있음" else "없음"}")

        // TODO: 나중에 서버로 데이터 전송
        // - nickname
        // - latitude, longitude, address
        // - profileBitmap (이미지를 Base64 또는 Multipart로 변환)

        Toast.makeText(this, "프로필 설정이 완료되었습니다!", Toast.LENGTH_SHORT).show()

        // HostHomeActivity로 이동
        val intent = Intent(this, HomeHostActivity::class.java).apply {
            // 필요한 데이터 전달 (예: 서비스 토큰 등)
            putExtra("NICKNAME", nickname)
            putExtra("ADDRESS", address)
        }
        startActivity(intent)

        // 이전 Activity들 모두 종료 (뒤로가기로 로그인 화면 안 나오게)
        finishAffinity()
    }
}