package com.longtoast.bilbil

import android.content.Context
import android.content.Intent
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
import com.kakao.sdk.user.UserApiClient // 카카오 SDK import
import com.kakao.sdk.auth.model.OAuthToken // 카카오 토큰 모델
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.google.gson.Gson // 🚨 Gson 임포트 추가
import java.security.MessageDigest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ... (getHashKey 함수는 그대로 둠) ...
    fun getHashKey(context: Context) {
        try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            for (signature in info.signatures!!) {
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                Log.d("KeyHash", Base64.encodeToString(md.digest(), Base64.NO_WRAP))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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

    private fun startKakaoLogin() {
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

    private fun handleKakaoLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Log.e("KAKAO", "카카오 로그인 실패", error)
            Toast.makeText(this, "카카오 로그인 실패: ${error.message}", Toast.LENGTH_LONG).show()
        } else if (token != null) {
            Log.i("KAKAO", "카카오 로그인 성공, Access Token 획득")
            Toast.makeText(this, "카카오 토큰 획득 성공", Toast.LENGTH_SHORT).show()
            sendTokenToServer(token.accessToken)
        }
    }

    // 서버 통신 (Retrofit 사용)
    private fun sendTokenToServer(kakaoAccessToken: String) {
        val requestBody = KakaoTokenRequest(kakaoAccessToken)
        val call = RetrofitClient.getApiService().loginWithKakaoToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {

            // -------------------------------------------------
            // 🚨 [수정됨] onResponse 함수
            // -------------------------------------------------
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                if (response.isSuccessful && response.body() != null) {

                    // 1. [수정] data 필드를 MemberTokenResponse로 안전하게 파싱
                    val rawData = response.body()?.data
                    val gson = Gson()
                    val memberTokenResponse: MemberTokenResponse? = try {
                        // GSON을 사용해 rawData(Map)를 MemberTokenResponse 객체로 변환
                        gson.fromJson(gson.toJsonTree(rawData), MemberTokenResponse::class.java)
                    } catch (e: Exception) {
                        Log.e("SERVER_AUTH", "MemberTokenResponse 파싱 실패", e)
                        null
                    }

                    if (memberTokenResponse != null) {
                        Log.d("SERVER_AUTH", "✅ 서버 인증 성공! 응답: $memberTokenResponse")

                        // 2. -------------------------------------------------
                        //    🚨 [핵심] 서비스 토큰 저장
                        // -------------------------------------------------
                        if (memberTokenResponse.serviceToken != null) {
                            AuthTokenManager.saveToken(memberTokenResponse.serviceToken)
                        } else {
                            Log.w("SERVER_AUTH", "⚠️ 서버가 serviceToken을 null로 보냈습니다. (로그인은 성공)")
                        }
                        // -------------------------------------------------

                        // 3. 주소 정보 확인 및 화면 이동
                        val isAddressMissing = memberTokenResponse.address.isNullOrEmpty() ||
                                memberTokenResponse.locationLatitude == null ||
                                memberTokenResponse.locationLongitude == null

                        if (isAddressMissing) {
                            Log.d("SERVER_AUTH", "🚨 주소 정보 누락! 지도 설정 필요.")
                            val intent = Intent(this@MainActivity, SettingMapActivity::class.java).apply {
                                putExtra("USER_NICKNAME", memberTokenResponse.nickname)
                                putExtra("SETUP_ADDRESS_NEEDED", true)
                            }
                            startActivity(intent)
                        } else {
                            Log.d("SERVER_AUTH", "✅ 로그인 성공! 기존 회원 메인 화면 이동.")
                            Toast.makeText(this@MainActivity, "${memberTokenResponse.nickname}님 환영합니다.", Toast.LENGTH_LONG).show()
                            val intent = Intent(this@MainActivity, HomeHostActivity::class.java)
                            startActivity(intent)
                            finish() // 로그인 화면 종료
                        }

                    } else {
                        Log.e("SERVER_AUTH", "서버 응답 data를 MemberTokenResponse로 변환 실패. rawData: $rawData")
                        Toast.makeText(this@MainActivity, "서버 인증 실패 (응답 형식 오류)", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e("SERVER_AUTH", "서버 응답 실패: ${response.code()}. 메시지: ${response.errorBody()?.string()}")
                    Toast.makeText(this@MainActivity, "서버 인증 실패: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("SERVER_AUTH", "서버 통신 오류", t)
                Toast.makeText(this@MainActivity, "로컬호스트 서버 접속 오류", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun startNaverLogin() {
        // ... (네이버 로그인 로직은 나중에 구현)
    }
}