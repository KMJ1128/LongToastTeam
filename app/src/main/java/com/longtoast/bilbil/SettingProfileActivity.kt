package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.InputStream

class SettingProfileActivity : AppCompatActivity() {

    private lateinit var imageProfile: ImageView
    private lateinit var fabChangePhoto: FloatingActionButton
    private lateinit var editNickname: EditText
    private lateinit var textLocationInfo: TextView
    private lateinit var buttonComplete: Button

    private var profileBitmap: Bitmap? = null

    // Intent로 받은 데이터
    private var userNickname: String = ""
    private var serviceToken: String? = null
    private var userId: Int = 0
    private var userName: String? = null
    private var pendingNickname: String = ""

    private val CAMERA_PERMISSION_CODE = 100

    // 갤러리에서 이미지 선택
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageResult(uri) }
    }

    // 카메라 촬영 결과
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            profileBitmap = it
            imageProfile.setImageBitmap(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_profile)

        getIntentData()
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
            "USER_ID: $userId, SERVICE_TOKEN: ${serviceToken?.take(10)}..."
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
        fabChangePhoto.setOnClickListener { showImagePickerDialog() }
        buttonComplete.setOnClickListener { onCompleteButtonClicked() }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("갤러리에서 선택", "카메라 촬영", "취소")

        AlertDialog.Builder(this)
            .setTitle("프로필 사진 선택")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> openCamera()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            takePictureLauncher.launch(null)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    private fun handleImageResult(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            profileBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            imageProfile.setImageBitmap(profileBitmap)
            Toast.makeText(this, "프로필 사진 설정됨", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "이미지 로드 오류", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Bitmap → Base64 문자열 변환
     */
    private fun bitmapToBase64(bitmap: Bitmap?): String? {
        if (bitmap == null) return null

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val bytes = baos.toByteArray()

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * 완료 버튼 클릭 → 지역 선택 화면 이동
     */
    private fun onCompleteButtonClicked() {
        val nickname = editNickname.text.toString().trim()

        if (nickname.length < 2) {
            Toast.makeText(this, "닉네임은 2자 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (serviceToken == null || userId == 0) {
            Toast.makeText(this, "인증 오류. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
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

    /**
     * 지역 선택 결과 수신
     */
    private val regionSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val address = result.data?.getStringExtra("FINAL_ADDRESS") ?: return@registerForActivityResult
            val lat = result.data?.getDoubleExtra("FINAL_LATITUDE", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("FINAL_LONGITUDE", 0.0) ?: 0.0

            textLocationInfo.text = address

            submitProfile(address, lat, lng)
        }

    /**
     * 프로필 제출 (Base64 방식)
     */
    private fun submitProfile(address: String, latitude: Double, longitude: Double) {
        val base64Image = bitmapToBase64(profileBitmap)

        val dto = MemberDTO(
            id = userId,
            nickname = pendingNickname,
            username = userName,
            address = address,
            locationLatitude = latitude,
            locationLongitude = longitude,
            creditScore = 720,
            profileImageUrl = base64Image,    // 🔥 Base64 저장
            createdAt = null
        )

        RetrofitClient.getApiService().updateProfile(dto)
            .enqueue(object : Callback<MsgEntity> {

                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingProfileActivity, "프로필 저장 완료!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SettingProfileActivity, HomeHostActivity::class.java))
                        finishAffinity()
                    } else {
                        Toast.makeText(this@SettingProfileActivity, "업데이트 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    Toast.makeText(this@SettingProfileActivity, "서버 연결 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }
}
