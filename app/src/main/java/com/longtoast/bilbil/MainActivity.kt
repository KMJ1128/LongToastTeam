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
import com.navercorp.nid.NaverIdLoginSDK // ✅ 이 줄을 추가합니다.
import com.navercorp.nid.oauth.OAuthLoginCallback // ✅ 이 줄을 추가합니다.
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.google.gson.Gson
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.google.gson.Gson // 🚨 Gson 임포트 추가
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

      //   💡 [임시 조치] 신규 회원가입 플로우 테스트를 위해 저장된 토큰 강제 초기화
      if (AuthTokenManager.getToken() != null) {
          AuthTokenManager.clearToken()
          AuthTokenManager.clearUserId()
            Log.w("JWT_CLEAN", "JWT 토큰 강제 초기화 완료. 신규 회원가입 플로우 시작.")       }
        // 1. JWT 토큰 상태 확인 및 자동 이동 (가장 먼저 실행)
        val token = AuthTokenManager.getToken()

        if (token != null) {
            val shortToken = token.substring(0, Math.min(token.length, 20)) + "..."
            Log.i("APP_AUTH_STATE", "✅ JWT 토큰 존재: $shortToken. 홈 화면으로 이동.")

            // 🔑 토큰이 유효하면 바로 홈 화면으로 이동
            val intent = Intent(this, HomeHostActivity::class.java)
            startActivity(intent)
            finish()
            return // 이후 로그인 UI 로직을 건너뜁니다.
        }

        // 2. 토큰이 없는 경우에만 로그인 UI 로드
        Log.w("APP_AUTH_STATE", "⚠️ JWT 토큰 없음. 로그인 UI 로드.")

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




    private fun startNaverLogin() {
        // 🚨 [Naver SDK 실제 연동 코드]
        NaverIdLoginSDK.authenticate(this, object : OAuthLoginCallback {
            override fun onSuccess() {
                val naverAccessToken = NaverIdLoginSDK.getAccessToken()
                naverAccessToken?.let {
                    Log.i("NAVER", "네이버 로그인 성공, Access Token 획득")
                    // 서버로 토큰 전송 (이미 구현된 함수)
                    sendNaverTokenToServer(it)
                }
            }
            override fun onFailure(httpStatus: Int, message: String) {
                Log.e("NAVER", "네이버 로그인 실패: $message")
                Toast.makeText(this@MainActivity, "네이버 로그인 실패: $message", Toast.LENGTH_LONG).show()
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e("NAVER", "네이버 로그인 에러: $message")
            }
            fun onCancel() {
                Log.i("NAVER", "네이버 로그인 취소")
            }
        })
    }

    /**
     * 네이버 Access Token을 서버로 전송하고 인증 응답을 처리하는 함수
     */
    private fun sendNaverTokenToServer(naverAccessToken: String) {
        val requestBody = NaverTokenRequest(naverAccessToken)
        val call = RetrofitClient.getApiService().loginWithNaverToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {
            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                handleServerAuthResponse(response)
            }

            override fun onFailure(call: Call<MsgEntity>, t: Throwable) {
                Log.e("SERVER_AUTH_NAVER", "서버 통신 오류", t)
                Toast.makeText(this@MainActivity, "로컬호스트 서버 접속 오류", Toast.LENGTH_LONG).show()
            }
        })
    }

    // -----------------------------------------------------
    // ✅ [추가/재활용] 서버 인증 응답 공통 처리 함수
    // -----------------------------------------------------
    private fun handleServerAuthResponse(response: Response<MsgEntity>) {
        if (response.isSuccessful && response.body() != null) {

            val rawData = response.body()?.data
            val gson = Gson()
            val memberTokenResponse: MemberTokenResponse? = try {
                gson.fromJson(gson.toJsonTree(rawData), MemberTokenResponse::class.java)
            } catch (e: Exception) {
                Log.e("SERVER_AUTH", "MemberTokenResponse 파싱 실패", e)
                null
            }

            if (memberTokenResponse != null) {
                Log.d("SERVER_AUTH", "✅ 서버 인증 성공! 응답: $memberTokenResponse")

                if (memberTokenResponse.serviceToken != null) {
                    AuthTokenManager.saveToken(memberTokenResponse.serviceToken)
                    // userId 저장 (Long을 Int로 변환해서 저장)
                    AuthTokenManager.saveUserId(memberTokenResponse.userId.toInt())
                } else {
                    Log.w("SERVER_AUTH", "⚠️ 서버가 serviceToken을 null로 보냈습니다. (로그인은 성공)")
                }

                // 주소 정보 확인 및 화면 이동
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





    // 서버 통신 (Retrofit 사용)
    private fun sendTokenToServer(kakaoAccessToken: String) {
        val requestBody = KakaoTokenRequest(kakaoAccessToken)
        val call = RetrofitClient.getApiService().loginWithKakaoToken(requestBody)

        call.enqueue(object : Callback<MsgEntity> {

            override fun onResponse(call: Call<MsgEntity>, response: Response<MsgEntity>) {
                if (response.isSuccessful && response.body() != null) {

                    // 1. data 필드를 MemberTokenResponse로 안전하게 파싱
                    val rawData = response.body()?.data
                    val gson = Gson()
                    val memberTokenResponse: MemberTokenResponse? = try {
                        gson.fromJson(gson.toJsonTree(rawData), MemberTokenResponse::class.java)
                    } catch (e: Exception) {
                        Log.e("SERVER_AUTH", "MemberTokenResponse 파싱 실패", e)
                        null
                    }

                    if (memberTokenResponse != null) {
                        Log.d("SERVER_AUTH", "✅ 서버 인증 성공! 응답: $memberTokenResponse")

                        val tempServiceToken = memberTokenResponse.serviceToken
                        val tempUserId = memberTokenResponse.userId.toInt() // Long -> Int 변환

                        // 2. 주소 정보 확인 및 화면 이동 (회원가입/로그인 구분)
                        val isSetupNeeded = memberTokenResponse.address.isNullOrEmpty()

                        if (isSetupNeeded) {
                            Log.d("SERVER_AUTH", "🚨 신규 회원 또는 주소 정보 누락! 지도 설정 필요.")

                            // 🔑 SettingMapActivity 호출 시 JWT 및 ID, SETUP_MODE=true 전달
                            val intent = Intent(this@MainActivity, SettingMapActivity::class.java).apply {
                                putExtra("USER_NICKNAME", memberTokenResponse.nickname)
                                putExtra("SETUP_MODE", true)
                                putExtra("SERVICE_TOKEN", tempServiceToken)
                                putExtra("USER_ID", tempUserId)
                            }
                            startActivity(intent)

                        } else {
                            // 💡 [기존 회원] 주소 설정이 완료된 경우, 바로 토큰 저장 후 메인 화면으로 이동
                            if (tempServiceToken != null) {
                                AuthTokenManager.saveToken(tempServiceToken)
                                AuthTokenManager.saveUserId(tempUserId)
                            }

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
}