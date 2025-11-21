// MainActivity.kt with JWT save fix inserted
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
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.google.gson.Gson
import com.longtoast.bilbil.dto.NaverTokenRequest
import java.security.MessageDigest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

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

        val token = AuthTokenManager.getToken()
        if (token != null) {
            Log.i("APP_AUTH_STATE", "JWT 존재 → 홈 이동")
            startActivity(Intent(this, HomeHostActivity::class.java))
            finish()
            return
        }

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

        binding.buttonNaverLogin.setOnClickListener {
            Toast.makeText(this, "네이버 로그인 시작…", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "카카오 로그인 실패", Toast.LENGTH_LONG).show()
        } else if (token != null) {
            sendTokenToServer(token.accessToken)
        }
    }

    private fun startNaverLogin() {
        NaverIdLoginSDK.authenticate(this, object : OAuthLoginCallback {
            override fun onSuccess() {
                val naverAccessToken = NaverIdLoginSDK.getAccessToken()
                if (naverAccessToken != null) {
                    Log.i("NAVER", "네이버 로그인 성공 → 토큰 획득")
                    sendNaverTokenToServer(naverAccessToken)
                }
            }

            override fun onFailure(httpStatus: Int, message: String) {
                Toast.makeText(this@MainActivity, "네이버 로그인 실패", Toast.LENGTH_LONG).show()
            }

            override fun onError(errorCode: Int, message: String) {
                Toast.makeText(this@MainActivity, "네이버 오류", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun sendNaverTokenToServer(naverAccessToken: String) {
        val requestBody = NaverTokenRequest(naverAccessToken)
        val call = RetrofitClient.getApiService().loginWithNaverToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                handleServerAuthResponse(response)
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Toast.makeText(this@MainActivity, "서버 오류", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun handleServerAuthResponse(response: Response<MsgEntity>) {
        if (response.isSuccessful && response.body() != null) {
            val rawData = response.body()!!.data
            val gson = Gson()
            val memberTokenResponse: MemberTokenResponse? = try {
                gson.fromJson(gson.toJsonTree(rawData), MemberTokenResponse::class.java)
            } catch (e: Exception) {
                null
            }

            if (memberTokenResponse == null) {
                Toast.makeText(this, "파싱 오류", Toast.LENGTH_LONG).show()
                return
            }

            val token = memberTokenResponse.serviceToken
            val userId = memberTokenResponse.userId.toInt()
            val nickname = memberTokenResponse.nickname

            val isAddressMissing =
                memberTokenResponse.address.isNullOrEmpty() ||
                        memberTokenResponse.locationLatitude == null ||
                        memberTokenResponse.locationLongitude == null

            if (isAddressMissing) {
                // ⭐ MUST HAVE: 신규 유저 주소 설정 전에 JWT 저장
                if (token != null) {
                    AuthTokenManager.saveToken(token)
                    AuthTokenManager.saveUserId(userId)
                    Log.d("AUTH", "JWT 저장 완료: 신규 유저 주소 설정 단계")
                }

                val intent = Intent(this@MainActivity, SettingMapActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra("SERVICE_TOKEN", token)
                    putExtra("USER_NICKNAME", nickname)
                    putExtra("SETUP_MODE", true)
                }
                startActivity(intent)
                return
            }

            // 기존 유저 → 토큰 저장 후 홈 이동
            if (token != null) {
                AuthTokenManager.saveToken(token)
                AuthTokenManager.saveUserId(userId)
            }

            startActivity(Intent(this, HomeHostActivity::class.java))
            finish()

        } else {
            Toast.makeText(this, "서버 응답 실패", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendTokenToServer(kakaoAccessToken: String) {
        val requestBody = KakaoTokenRequest(kakaoAccessToken)
        val call = RetrofitClient.getApiService().loginWithKakaoToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                handleServerAuthResponse(response)
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Toast.makeText(this@MainActivity, "서버 오류", Toast.LENGTH_LONG).show()
            }
        })
    }
}