package com.longtoast.bilbil

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.longtoast.bilbil.databinding.ActivitySettingMapBinding

import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView

class SettingMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingMapBinding
    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySettingMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.mapView

        // 4. MapView의 start() 호출 및 콜백 구현
        mapView.start(object : MapLifeCycleCallback() {

            override fun onMapDestroy() {
                // 지도 API 가 정상적으로 종료될 때 호출됨
            }

            override fun onMapError(error: Exception) {
                // 인증 실패 및 지도 사용 중 에러가 발생할 때 호출됨
                // Logcat에서 에러 메시지를 확인하여 인증/네트워크 문제 확인
                android.util.Log.e("KAKAO_MAP", "Map Error: ${error.message}")
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                // 인증 후 API 가 정상적으로 실행될 때 호출됨
            }
        })

        // Window Insets 설정 (기존 코드와 동일)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // SDK 초기화가 성공하면 이제 이 코드가 NullPointerException 없이 실행됩니다.
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // MapView.onDestroy()는 최신 SDK 버전에서 수동 호출하지 않는 것이 일반적입니다.
    }
}