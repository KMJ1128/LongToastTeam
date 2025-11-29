package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
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

class SettingProfileActivity : AppCompatActivity() {

    // region Views
    private lateinit var imageProfile: ImageView
    private lateinit var fabChangePhoto: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var editNickname: EditText
    private lateinit var textLocationInfo: TextView
    private lateinit var buttonComplete: Button
    // endregion

    private var profileBitmap: Bitmap? = null
    private var profileImageUri: Uri? = null

    // Intent 데이터
    private var userId = 0
    private var serviceToken: String? = null
    private var pendingNickname: String = ""
    private var verifiedPhoneNumber: String? = null
    private var userName: String? = null

    private val CAMERA_PERMISSION_CODE = 100

    // -------------------------------
    // 🔥 Region launcher (핵심)
    // -------------------------------
    private val regionSelectionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

            val address = result.data!!.getStringExtra("FINAL_ADDRESS") ?: return@registerForActivityResult
            val latitude = result.data!!.getDoubleExtra("FINAL_LATITUDE", 0.0)
            val longitude = result.data!!.getDoubleExtra("FINAL_LONGITUDE", 0.0)

            // 🔥 recreate 시점에서도 crash 방지
            if (::textLocationInfo.isInitialized) {
                textLocationInfo.text = address
            }

            submitProfile(address, latitude, longitude)
        }

    // -------------------------------
    // 이미지 선택 런처
    // -------------------------------
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImageResult(it) } }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && profileImageUri != null) {
            handleImageResult(profileImageUri!!)
        }
    }

    // -------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_profile)

        getIntentData()
        initViews()
        setupListeners()
    }

    private fun getIntentData() {
        userId = intent.getIntExtra("USER_ID", 0)
        serviceToken = intent.getStringExtra("SERVICE_TOKEN")
        verifiedPhoneNumber = intent.getStringExtra("VERIFIED_PHONE_NUMBER")
        userName = intent.getStringExtra("USER_NAME")
    }

    private fun initViews() {
        imageProfile = findViewById(R.id.image_profile)
        fabChangePhoto = findViewById(R.id.fab_change_photo)
        editNickname = findViewById(R.id.edit_nickname)
        textLocationInfo = findViewById(R.id.text_location_info)
        buttonComplete = findViewById(R.id.button_complete)
    }

    private fun setupListeners() {
        fabChangePhoto.setOnClickListener { showImagePickerDialog() }
        buttonComplete.setOnClickListener { onCompleteButtonClicked() }
    }

    // -------------------------------------------------------
    // 프로필 완료 버튼 → RegionSelectionActivity 호출
    // -------------------------------------------------------
    private fun onCompleteButtonClicked() {
        val nickname = editNickname.text.toString().trim()

        if (nickname.length < 2) {
            Toast.makeText(this, "닉네임은 2자 이상입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingNickname = nickname

        val intent = Intent(this, RegionSelectionActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("SERVICE_TOKEN", serviceToken)
            putExtra("USER_NICKNAME", nickname)
            putExtra("VERIFIED_PHONE_NUMBER", verifiedPhoneNumber)
            putExtra(RegionSelectionActivity.EXTRA_MODE, RegionSelectionActivity.MODE_PROFILE)
        }

        regionSelectionLauncher.launch(intent)
    }

    // -------------------------------------------------------
    // RegionSelectionActivity → SettingProfile 복귀 → 서버 저장
    // -------------------------------------------------------
    private fun submitProfile(address: String, latitude: Double, longitude: Double) {

        val updateRequest = MemberDTO(
            id = userId,
            nickname = pendingNickname,
            username = userName,
            address = address,
            phoneNumber = verifiedPhoneNumber,
            locationLatitude = latitude,
            locationLongitude = longitude,
            creditScore = 720,
            profileImageUrl = null,
            createdAt = null
        )

        val memberJson = Gson().toJson(updateRequest)
        val memberRequestBody = memberJson.toRequestBody("application/json".toMediaType())

        // 이미지 multipart 변환
        var imagePart: MultipartBody.Part? = null

        if (profileBitmap != null) {
            try {
                val file = File(cacheDir, "profile_upload.jpg")
                val fos = FileOutputStream(file)
                profileBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()

                val req = file.asRequestBody("image/jpeg".toMediaType())
                imagePart = MultipartBody.Part.createFormData("profileImage", file.name, req)

            } catch (e: Exception) {
                Log.e("PROFILE_IMAGE", "이미지 변환 실패", e)
            }
        }

        RetrofitClient.getApiService().updateProfile(
            memberRequestBody,
            imagePart
        ).enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                if (!response.isSuccessful) {
                    Toast.makeText(this@SettingProfileActivity, "프로필 저장 실패", Toast.LENGTH_LONG).show()
                    return
                }

                // 성공 → 홈으로 이동
                Toast.makeText(this@SettingProfileActivity, "프로필 설정 완료!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@SettingProfileActivity, HomeHostActivity::class.java)
                startActivity(intent)
                finishAffinity()
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Toast.makeText(this@SettingProfileActivity, "서버 통신 오류", Toast.LENGTH_LONG).show()
            }
        })
    }

    // -------------------------------------------------------
    // 이미지 처리
    // -------------------------------------------------------
    private fun showImagePickerDialog() {
        AlertDialog.Builder(this)
            .setTitle("프로필 사진 변경")
            .setItems(arrayOf("갤러리", "카메라", "취소")) { dialog, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> openCamera()
                    2 -> dialog.dismiss()
                }
            }.show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            return
        }

        val file = File.createTempFile("profile_", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        profileImageUri = uri

        takePictureLauncher.launch(uri)
    }

    private fun handleImageResult(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri)
            profileBitmap = BitmapFactory.decodeStream(input)
            input?.close()
            imageProfile.setImageBitmap(profileBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
