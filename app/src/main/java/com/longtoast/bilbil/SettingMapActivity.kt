package com.longtoast.bilbil

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.kakao.vectormap.MapView
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk

class SettingMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_map)



        // 2. MapView 가져오기
        mapView = findViewById(R.id.map_view)

        // 3. MapView 시작 (v2에서는 콜백 객체를 바로 생성)
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    // 지도 종료 시 필요한 처리
                }

                override fun onMapError(error: Exception) {
                    error.printStackTrace()
                    Log.e("MAP_ERROR", "Map error: ${error.message}")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    Log.d("MAP_READY", "Map is ready!")
                    // 지도 초기 위치 설정
                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                        LatLng.from(37.402005, 127.108621)
                    )
                    kakaoMap.moveCamera(cameraUpdate) // 즉시 이동
                    //hi
                    // 애니메이션 이동 예시
                    // kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(500, true, true))
                }
            }
        )
    }

    // Activity 라이프사이클과 MapView 연동
    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        //mapView.stop() // 종료 시 반드시 호출
    }
}
