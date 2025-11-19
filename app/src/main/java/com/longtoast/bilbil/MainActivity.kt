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
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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

        // 💡 [임시 조치] 신규 회원가입 플로우 테스트를 위해 저장된 토큰 강제 초기화
        if (AuthTokenManager.getToken() != null) {
            AuthTokenManager.clearToken()
            AuthTokenManager.clearUserId()
            Log.w("JWT_CLEAN", "JWT 토큰 강제 초기화 완료. 신규 회원가입 플로우 시작.")       }


        // 1) 기존 토큰 있으면 바로 메인 이동
        val token = AuthTokenManager.getToken()
        if (token != null) {
            Log.i("APP_AUTH", "JWT 존재 → 홈 이동")
            startActivity(Intent(this, HomeHostActivity::class.java))
            finish()
            return
        }

        // 2) 로그인 화면 표시
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
            Toast.makeText(this, "카카오 로그인 시작…", Toast.LENGTH_SHORT).show()
            startKakaoLogin()
        }
    }

    private fun startKakaoLogin() {
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                handleKakaoLoginResult(token, error)
            }//
        } else {
            UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
                handleKakaoLoginResult(token, error)
            }
        }
    }

    private fun handleKakaoLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Toast.makeText(this, "카카오 로그인 실패", Toast.LENGTH_LONG).show()
        } else if (token != null) {
            sendTokenToServer(token.accessToken)
        }
    }

    private fun sendTokenToServer(kakaoAccessToken: String) {
        val requestBody = KakaoTokenRequest(kakaoAccessToken)
        val call = RetrofitClient.getApiService().loginWithKakaoToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {

            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {

                if (!response.isSuccessful || response.body() == null) {
                    Toast.makeText(this@MainActivity, "서버 응답 오류", Toast.LENGTH_LONG).show()
                    return
                }

                val rawData = response.body()!!.data
                val gson = Gson()

                val memberTokenResponse: MemberTokenResponse? = try {
                    gson.fromJson(gson.toJsonTree(rawData), MemberTokenResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                if (memberTokenResponse == null) {
                    Toast.makeText(this@MainActivity, "데이터 파싱 오류", Toast.LENGTH_LONG).show()
                    return
                }

                val tempToken = memberTokenResponse.serviceToken
                val tempUserId = memberTokenResponse.userId.toInt()
                val nickname = memberTokenResponse.nickname

                // 🚨 [수정된 핵심 로직] address 및 nickname 유무 확인
                val hasAddress = !memberTokenResponse.address.isNullOrEmpty()
                val hasNickname = !nickname.isNullOrEmpty()

                // 1. address가 없는 경우 (무조건 지도 설정부터)
                if (!hasAddress) {
                    Log.d("SERVER_AUTH", "신규 회원: 주소 설정 필요 → SettingMapActivity로 이동")

                    val intent = Intent(this@MainActivity, SettingMapActivity::class.java).apply {
                        putExtra("USER_NICKNAME", nickname)
                        putExtra("SETUP_MODE", true)
                        putExtra("USER_ID", tempUserId)
                        putExtra("SERVICE_TOKEN", tempToken)
                    }
                    startActivity(intent)
                    finish()
                }
                // 2. address는 있는데 nickname이 없는 경우 (Map 건너뛰고 Profile 설정)
                else if (hasAddress && !hasNickname) {
                    Log.d("SERVER_AUTH", "기존 회원: 주소는 있으나 닉네임 설정 필요 → SettingProfileActivity로 이동")

                    val intent = Intent(this@MainActivity, SettingProfileActivity::class.java).apply {
                        putExtra("USER_NICKNAME", nickname)
                        putExtra("USER_ID", tempUserId)
                        putExtra("SERVICE_TOKEN", tempToken)

                        // MapActivity를 건너뛰기 위해 서버에서 받은 위치 정보를 전달
                        putExtra("LATITUDE", memberTokenResponse.locationLatitude ?: 0.0)
                        putExtra("LONGITUDE", memberTokenResponse.locationLongitude ?: 0.0)
                        putExtra("ADDRESS", memberTokenResponse.address)
                    }
                    startActivity(intent)
                    finish()
                }
                // 3. address, nickname 모두 있는 경우 (기존 회원, 설정 완료)
                else {
                    // 기존 회원 → 토큰 저장 후 메인 이동
                    if (tempToken != null) {
                        AuthTokenManager.saveToken(tempToken)
                        AuthTokenManager.saveUserId(tempUserId)
                    }

                    Log.d("SERVER_AUTH", "기존 회원: 설정 완료 → HomeHostActivity로 이동")
                    startActivity(Intent(this@MainActivity, HomeHostActivity::class.java))
                    finish()
                }
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Toast.makeText(this@MainActivity, "서버 접속 실패", Toast.LENGTH_LONG).show()
            }
        })
    }
}