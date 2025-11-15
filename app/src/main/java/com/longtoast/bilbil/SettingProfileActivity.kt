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
import com.longtoast.bilbil.api.ApiService
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.MsgEntity
import java.io.File
import java.io.IOException
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.longtoast.bilbil.api.RetrofitClient // BASE_URL을 가져오기 위해 유지

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

        getIntentData()
        initViews()
        displayData()
        setupListeners()
    }

    private fun getIntentData() {
        latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        longitude = intent.getDoubleExtra("LONGITUDE", 0.0)
        address = intent.getStringExtra("ADDRESS") ?: ""
        userNickname = intent.getStringExtra("USER_NICKNAME") ?: ""

        serviceToken = intent.getStringExtra("SERVICE_TOKEN")
        userId = intent.getIntExtra("USER_ID", 0)


        Log.d("SettingProfile", "받은 데이터 - 위도: $latitude, 경도: $longitude")
        Log.d("SettingProfile", "주소: $address, 닉네임: $userNickname")
        Log.d("SettingProfile", "인증 정보 - USER_ID: $userId, SERVICE_TOKEN: ${serviceToken?.substring(0, Math.min(serviceToken?.length ?: 0, 10))}...")
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

        if (address.isNotEmpty()) {
            textLocationInfo.text = address
        } else {
            textLocationInfo.text = "위도: $latitude, 경도: $longitude"
        }
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

        // 1. DTO 생성 (MemberDTO는 8개 필드를 String? 타입으로 가정)
        val updateRequest = MemberDTO(
            id = userId,
            nickname = nickname,
            address = address,
            locationLatitude = latitude,
            locationLongitude = longitude,
            creditScore = 720,
            profileImageUrl = null,
            createdAt = null
        )

        // 2. 🔑 [핵심] API 호출 전에 AuthTokenManager에 토큰/ID를 저장합니다.
        AuthTokenManager.saveToken(serviceToken!!)
        AuthTokenManager.saveUserId(userId)
        Log.d("PROFILE_COMPLETE", "✅ JWT 및 User ID 저장 완료. API 호출 시작.")


        // 3. 🔑 [최종 해결책] 토큰을 헤더에 직접 주입하는 임시 Retrofit 클라이언트 생성 및 호출
        val tempApiService = createTempApiServiceWithToken(serviceToken!!)

        tempApiService.updateProfile(updateRequest)
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    if (response.isSuccessful) {
                        Log.d("PROFILE_COMPLETE", "✅ 프로필 업데이트 성공 (200/201). 홈 이동.")
                        Toast.makeText(this@SettingProfileActivity, "프로필 설정 및 저장 완료!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@SettingProfileActivity, HomeHostActivity::class.java)
                        startActivity(intent)
                        finishAffinity()
                    } else if (response.code() == 403 || response.code() == 401) {
                        // 🚨 [403/401 에러 감지] 토큰이 무효하거나 만료됨.
                        Log.e("PROFILE_API", "프로필 업데이트 실패: 인증 거부 (403/401). 토큰 무효화.")
                        Toast.makeText(this@SettingProfileActivity, "인증 오류. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                        AuthTokenManager.clearToken()
                        startActivity(Intent(this@SettingProfileActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                    else {
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


    /**
     * 🔑 [핵심 메서드] API 호출 시점에 토큰을 직접 주입하는 임시 Retrofit 인스턴스 생성
     */
    private fun createTempApiServiceWithToken(token: String): ApiService {
        val authInterceptor = Interceptor { chain ->
            val newRequest = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
            chain.proceed(newRequest)
        }

        // 💡 BASE_URL을 RetrofitClient.kt에서 직접 참조합니다. (하드코딩 방지)
        val BASE_URL_TEMP = try {
            val field = RetrofitClient::class.java.getDeclaredField("BASE_URL")
            field.isAccessible = true
            field.get(RetrofitClient) as String
        } catch (e: Exception) {
            // Reflection이 실패하면, 현재 알려주신 IP를 사용합니다.
            Log.e("RETROFIT_INIT", "BASE_URL Reflection 실패, 하드코딩된 주소 사용.")
            "http://172.16.102.73:8080/"
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL_TEMP)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}