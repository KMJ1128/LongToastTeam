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
import com.google.gson.Gson
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.util.Utility
import com.kakao.sdk.user.UserApiClient
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.databinding.ActivityMainBinding
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
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
        Log.d("APP_ID_CHECK", "BuildConfig.APPLICATION_ID = ${BuildConfig.APPLICATION_ID}")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        val keyHash = Utility.getKeyHash(this)
        Log.i("KeyHash", "keyHash = $keyHash")

        AuthTokenManager.clearToken()
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

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS

            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 1001)
            }
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
            val memberTokenResponse: MemberTokenResponse? =
                try {
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
                if (token != null) {
                    onLoginSuccess(token, userId)
                }

                val intent = Intent(this@MainActivity, SettingProfileActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra("SERVICE_TOKEN", token)
                    putExtra("SETUP_MODE", true)
                    putExtra("USER_NICKNAME", nickname)
                }
                startActivity(intent)
                return
            }

            if (token != null) {
                onLoginSuccess(token, userId)
            }

            startActivity(Intent(this, HomeHostActivity::class.java))
            finish()

        } else {
            Toast.makeText(this, "서버 응답 실패", Toast.LENGTH_LONG).show()
        }
    }

    // 🔥 로그인 성공 시 반드시 호출되는 FCM 토큰 업로드 포함 함수
    private fun onLoginSuccess(jwt: String, userId: Int) {
        AuthTokenManager.saveToken(jwt)
        AuthTokenManager.saveUserId(userId)

        // 🔥 핵심: 로그인 직후 FCM 토큰을 서버에 업로드
        FcmTokenManager.uploadCurrentToken()
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
