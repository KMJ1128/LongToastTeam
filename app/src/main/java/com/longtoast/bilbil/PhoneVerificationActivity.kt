package com.longtoast.bilbil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.VerificationResponse
import com.longtoast.bilbil.dto.VerifyRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PhoneVerificationActivity : AppCompatActivity() {

    private lateinit var editPhoneNumber: EditText
    private lateinit var buttonRequestVerify: Button
    private lateinit var buttonConfirmVerify: Button
    private lateinit var textVerifyStatus: TextView
    private lateinit var buttonNext: Button // 다음 단계로 이동 버튼

    private var userId: Int = 0
    private var serviceToken: String? = null
    private var userNickname: String? = null
    private var userName: String? = null

    private var isVerified: Boolean = false
    private var currentPhoneNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 🚨 activity_phone_verification.xml 레이아웃 파일이 필요합니다.
        setContentView(R.layout.activity_phone_verification)

        getIntentData()
        initViews()
        setupListeners()
    }

    private fun getIntentData() {
        userId = intent.getIntExtra("USER_ID", 0)
        serviceToken = intent.getStringExtra("SERVICE_TOKEN")
        userNickname = intent.getStringExtra("USER_NICKNAME")
        userName = intent.getStringExtra("USER_NAME")
    }

    private fun initViews() {
        editPhoneNumber = findViewById(R.id.edit_phone_number)
        buttonRequestVerify = findViewById(R.id.button_request_verify)
        buttonConfirmVerify = findViewById(R.id.button_confirm_verify)
        textVerifyStatus = findViewById(R.id.text_verify_status)
        buttonNext = findViewById(R.id.button_next)

        buttonConfirmVerify.isEnabled = false
        buttonNext.isEnabled = false
        buttonNext.text = "인증 완료 후 다음 단계"
    }

    private fun setupListeners() {
        buttonRequestVerify.setOnClickListener {
            val phone = editPhoneNumber.text.toString().trim()
            if (phone.isEmpty() || phone.length < 10) {
                Toast.makeText(this, "유효한 전화번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestVerification(phone)
        }

        buttonConfirmVerify.setOnClickListener {
            val phone = editPhoneNumber.text.toString().trim()
            if (!isVerified && phone.isNotEmpty()) {
                confirmVerification(phone)
            }
        }

        buttonNext.setOnClickListener {
            if (isVerified && currentPhoneNumber != null) {
                // 인증된 정보를 SettingProfileActivity로 전달
                val intent = Intent(this, SettingProfileActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra("SERVICE_TOKEN", serviceToken)
                    putExtra("USER_NICKNAME", userNickname)
                    putExtra("USER_NAME", userName)
                    // 🟢 [핵심] 인증된 번호 전달
                    putExtra("VERIFIED_PHONE_NUMBER", currentPhoneNumber)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    // 1단계: 인증 요청 API 호출
    private fun requestVerification(phoneNumber: String) {
        buttonRequestVerify.isEnabled = false
        textVerifyStatus.text = "인증 요청 중..."

        RetrofitClient.getApiService().requestVerification(VerifyRequest(phoneNumber))
            .enqueue(object : Callback<MsgEntity> {
                override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                    buttonRequestVerify.isEnabled = true

                    // ------------------------------
                    // 🔥 (1) 실패 응답 처리 (400 등)
                    // ------------------------------
                    if (!response.isSuccessful) {
                        val errorText = response.errorBody()?.string()
                        Log.e("VERIFY_FLOW", "실패 응답: $errorText")

                        try {
                            val errorMsg = Gson().fromJson(errorText, MsgEntity::class.java)

                            if (errorMsg.message == "이미 다른 소셜로그인으로 가입된 사용자입니다") {
                                Toast.makeText(
                                    this@PhoneVerificationActivity,
                                    "이미 다른 소셜로그인으로 가입된 사용자입니다",
                                    Toast.LENGTH_LONG
                                ).show()

                                textVerifyStatus.text = "이미 가입된 번호입니다."
                                return
                            }

                        } catch (e: Exception) {
                            Log.e("VERIFY_FLOW", "에러 파싱 실패", e)
                        }

                        Toast.makeText(
                            this@PhoneVerificationActivity,
                            "인증 요청 실패",
                            Toast.LENGTH_SHORT
                        ).show()
                        textVerifyStatus.text = "인증 요청 실패"
                        return
                    }

                    // ------------------------------
                    // 🔥 (2) 성공 응답 처리
                    // ------------------------------
                    val entity = response.body()
                    val dataJson = Gson().toJson(entity?.data)
                    val responseData = Gson().fromJson(dataJson, VerificationResponse::class.java)

                    val smsUrl = responseData.smsUrl
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(smsUrl)))

                    textVerifyStatus.text =
                        "문자를 보낸 뒤 '인증 완료' 버튼을 눌러주세요."

                    buttonConfirmVerify.isEnabled = true
                    editPhoneNumber.isEnabled = false
                    buttonRequestVerify.visibility = View.GONE
                }

                override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                    buttonRequestVerify.isEnabled = true
                    textVerifyStatus.text = "네트워크 오류 발생."
                    Toast.makeText(this@PhoneVerificationActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            })
    }


    // 3단계: 인증 확인 API 호출
    private fun confirmVerification(phoneNumber: String) {
        buttonConfirmVerify.isEnabled = false
        textVerifyStatus.text = "인증 확인 중..."

        RetrofitClient.getApiService().confirmVerification(VerifyRequest(phoneNumber)).enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                buttonConfirmVerify.isEnabled = true
                if (response.isSuccessful) {
                    isVerified = true
                    currentPhoneNumber = phoneNumber

                    Toast.makeText(this@PhoneVerificationActivity, "🎉 전화번호 인증 성공!", Toast.LENGTH_LONG).show()

                    textVerifyStatus.text = "✅ 인증 완료! 다음 버튼을 눌러 프로필 설정을 계속해주세요."
                    buttonConfirmVerify.visibility = View.GONE // 완료 버튼 숨김

                    buttonNext.isEnabled = true // 다음 단계 버튼 활성화

                } else {
                    Log.e("VERIFY_FLOW", "인증 확인 실패: ${response.code()}")
                    textVerifyStatus.text = "인증 실패. 문자를 보냈는지, 번호가 올바른지 확인해주세요."
                    // 실패 시 재요청 가능하도록 상태 복구
                    editPhoneNumber.isEnabled = true
                    buttonRequestVerify.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                buttonConfirmVerify.isEnabled = true
                textVerifyStatus.text = "서버 연결 오류 발생."
                Toast.makeText(this@PhoneVerificationActivity, "서버 연결 오류", Toast.LENGTH_SHORT).show()
            }
        })
    }
}