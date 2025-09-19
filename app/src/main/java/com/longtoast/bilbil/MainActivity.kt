package com.longtoast.bilbil

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.longtoast.bilbil.databinding.ActivityMainBinding
import com.longtoast.bilbil.api.RetrofitClient // RetrofitClient import
import com.longtoast.bilbil.dto.KakaoTokenRequest // DTO import
import com.longtoast.bilbil.dto.MsgEntity // DTO import
import com.kakao.sdk.user.UserApiClient // 🚨 카카오 SDK import 활성화
import com.kakao.sdk.auth.model.OAuthToken // 카카오 토큰 모델
import java.security.MessageDigest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// 네이버 SDK 관련 import는 일단 주석 처리 유지

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ... (getHashKey 함수는 생략, 그대로 유지) ...
    fun getHashKey(context: Context) { /* ... */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getHashKey(this)
        setupLoginButtons()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupLoginButtons() {
        binding.buttonKakaoLogin.setOnClickListener {
            Toast.makeText(this, "카카오 로그인 시작...", Toast.LENGTH_SHORT).show()
            startKakaoLogin()
        }
        binding.buttonNaverLogin.setOnClickListener {
            Toast.makeText(this, "네이버 로그인 시작...", Toast.LENGTH_SHORT).show()
            startNaverLogin()
        }
    }

    /**
     * ✅ 카카오 SDK를 사용하여 로그인 프로세스를 시작하고 토큰을 서버로 전달합니다.
     */
    private fun startKakaoLogin() {
        // 1. 카카오톡 설치 유무에 따라 로그인 방식 분기
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                handleKakaoLoginResult(token, error)
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
                handleKakaoLoginResult(token, error)
            }
        }
    }

    // 카카오 로그인 결과 처리 및 서버 통신 시작
    private fun handleKakaoLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Log.e("KAKAO", "카카오 로그인 실패", error)
            Toast.makeText(this, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_LONG).show()
        } else if (token != null) {
            Log.i("KAKAO", "카카오 로그인 성공, Access Token 획득")
            Toast.makeText(this, "카카오 토큰 획득 성공", Toast.LENGTH_SHORT).show()

            // 🚨 2. 획득한 토큰을 우리 서버(로컬호스트)로 전송 🚨
            sendTokenToServer(token.accessToken)
        }
    }

    // 서버 통신 (Retrofit 사용)
    private fun sendTokenToServer(kakaoAccessToken: String) {
        val requestBody = KakaoTokenRequest(kakaoAccessToken)

        // RetrofitClient.getApiService()는 BASE_URL이 http://10.0.2.2:8080/로 설정되어야 합니다.
        val call = RetrofitClient.getApiService().loginWithKakaoToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                if (response.isSuccessful) {
                    val memberTokenResponse = response.body()?.data
                    if (memberTokenResponse != null) {
                        Log.d("SERVER_AUTH", "✅ 서버 인증 성공! 서비스 토큰 수신.")
                        Toast.makeText(this@MainActivity, "로그인 완료: ${memberTokenResponse.nickname}", Toast.LENGTH_LONG).show()

                        // 💡 TODO: 서비스 토큰 저장 및 메인 화면 이동 로직 구현
                    }
                } else {
                    Log.e("SERVER_AUTH", "서버 응답 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                    Toast.makeText(this@MainActivity, "서버 인증 실패", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("SERVER_AUTH", "서버 통신 오류", t)
                Toast.makeText(this@MainActivity, "로컬호스트 서버 접속 오류", Toast.LENGTH_LONG).show()
            }
        })
    }


    /**
     * TODO: 네이버 SDK를 사용하여 로그인 프로세스를 시작하는 함수 (나중에 구현)
     */
    private fun startNaverLogin() {
        // ... (네이버 로그인 로직은 나중에 구현)
    }
}