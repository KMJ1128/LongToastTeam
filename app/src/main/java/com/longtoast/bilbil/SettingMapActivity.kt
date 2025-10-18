package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.MapView
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.KakaoMapReadyCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap
    private lateinit var editSearchAddress: EditText
    private lateinit var buttonSearch: Button
    private lateinit var buttonCurrentLocation: Button
    private lateinit var buttonConfirm: Button
    private lateinit var textSelectedAddress: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLatitude: Double = 37.50115001650065
    private var currentLongitude: Double = 126.8675615713012
    private var currentAddress: String = ""

    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private val KAKAO_REST_API_KEY = "7a3a72c388ba6dfc6df8ca9715f284ff"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_map)

        val userNickname = intent.getStringExtra("USER_NICKNAME") ?: ""
        val isSetupNeeded = intent.getBooleanExtra("SETUP_ADDRESS_NEEDED", false)

        Log.d("SettingMap", "닉네임: $userNickname, 설정필요: $isSetupNeeded")

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initializeMap()
        setupListeners()
        checkAndRequestLocationPermission()
    }

    private fun initViews() {
        mapView = findViewById(R.id.map_view)
        editSearchAddress = findViewById(R.id.edit_search_address)
        buttonSearch = findViewById(R.id.button_search)
        buttonCurrentLocation = findViewById(R.id.button_current_location)
        buttonConfirm = findViewById(R.id.button_confirm_location)
        textSelectedAddress = findViewById(R.id.text_selected_address)
    }

    private fun setupListeners() {
        buttonSearch.setOnClickListener {
            val query = editSearchAddress.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAddress(query)
            } else {
                Toast.makeText(this, "주소를 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        editSearchAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = editSearchAddress.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchAddress(query)
                }
                true
            } else {
                false
            }
        }

        buttonCurrentLocation.setOnClickListener {
            getCurrentLocationAndMove()
        }

        buttonConfirm.setOnClickListener {
            onLocationConfirmed()
        }
    }

    private fun initializeMap() {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.d("MAP", "Map destroyed")
                }

                override fun onMapError(error: Exception) {
                    error.printStackTrace()
                    Log.e("MAP_ERROR", "Map error: ${error.message}")
                    Toast.makeText(
                        this@SettingMapActivity,
                        "지도 로드 오류",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    moveCameraTo(currentLatitude, currentLongitude)

                    Log.d("MAP", "지도 준비 완료")
                }
            }
        )
    }

    private fun checkAndRequestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocationAndMove()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "위치 권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
                getCurrentLocationAndMove()
            } else {
                Toast.makeText(
                    this,
                    "위치 권한이 필요합니다. 수동으로 위치를 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCurrentLocationAndMove() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "현재 위치를 가져오는 중...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude

                Log.d("GPS", "현재 위치: $currentLatitude, $currentLongitude")
                Toast.makeText(this, "현재 위치로 이동했습니다", Toast.LENGTH_SHORT).show()

                if (::kakaoMap.isInitialized) {
                    moveCameraTo(currentLatitude, currentLongitude)
                }
            } else {
                Log.w("GPS", "위치를 가져올 수 없습니다")
                Toast.makeText(this, "현재 위치를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Log.e("GPS", "위치 가져오기 실패", e)
            Toast.makeText(this, "위치 가져오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchAddress(query: String) {
        Toast.makeText(this, "검색 중...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    searchKakaoLocalAPI(query)
                }

                if (result != null) {
                    val lat = result.first
                    val lon = result.second
                    val addressName = result.third

                    currentLatitude = lat
                    currentLongitude = lon
                    currentAddress = addressName

                    moveCameraTo(lat, lon)
                    textSelectedAddress.text = addressName

                    Toast.makeText(
                        this@SettingMapActivity,
                        "검색 완료: $addressName",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@SettingMapActivity,
                        "검색 결과가 없습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("SEARCH", "주소 검색 실패", e)
                Toast.makeText(
                    this@SettingMapActivity,
                    "검색 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun searchKakaoLocalAPI(query: String): Triple<Double, Double, String>? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiUrl = "https://dapi.kakao.com/v2/local/search/keyword.json?query=$encodedQuery"

        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_API_KEY")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val documents = json.getJSONArray("documents")

                if (documents.length() > 0) {
                    val firstResult = documents.getJSONObject(0)
                    val lat = firstResult.getString("y").toDouble()
                    val lon = firstResult.getString("x").toDouble()
                    val addressName = firstResult.getString("place_name")

                    return Triple(lat, lon, addressName)
                }
            } else {
                Log.e("SEARCH", "API 응답 오류: $responseCode")
            }
        } finally {
            connection.disconnect()
        }

        return null
    }

    private fun getAddressFromCoordinates(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val address = withContext(Dispatchers.IO) {
                    reverseGeocodeKakaoAPI(lat, lon)
                }

                if (address != null) {
                    currentAddress = address
                    textSelectedAddress.text = address
                } else {
                    textSelectedAddress.text = "위도: $lat, 경도: $lon"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                textSelectedAddress.text = "위도: $lat, 경도: $lon"
            }
        }
    }

    private fun reverseGeocodeKakaoAPI(lat: Double, lon: Double): String? {
        val apiUrl = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lon&y=$lat"

        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_API_KEY")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val documents = json.getJSONArray("documents")

                if (documents.length() > 0) {
                    val firstResult = documents.getJSONObject(0)
                    val address = firstResult.getJSONObject("address")
                    return address.getString("address_name")
                }
            }
        } finally {
            connection.disconnect()
        }

        return null
    }

    private fun moveCameraTo(latitude: Double, longitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude

        val cameraUpdate = CameraUpdateFactory.newCenterPosition(
            LatLng.from(latitude, longitude)
        )
        kakaoMap.moveCamera(cameraUpdate)

        // 카메라 이동 후 주소 가져오기
        getAddressFromCoordinates(latitude, longitude)
    }

    private fun onLocationConfirmed() {
        Log.d("LOCATION_CONFIRM", "선택된 위치: $currentLatitude, $currentLongitude")
        Log.d("LOCATION_CONFIRM", "주소: $currentAddress")

        // 주소가 없어도 진행 가능하도록 수정
        if (currentAddress.isEmpty()) {
            currentAddress = "위도: $currentLatitude, 경도: $currentLongitude"
            Log.d("LOCATION_CONFIRM", "주소를 가져올 수 없어 좌표로 대체")
        }

        Toast.makeText(this, "위치가 설정되었습니다", Toast.LENGTH_SHORT).show()

        val receivedNickname = intent.getStringExtra("USER_NICKNAME")

        val newIntent = Intent(this, SettingProfileActivity::class.java).apply {
            putExtra("LATITUDE", currentLatitude)
            putExtra("LONGITUDE", currentLongitude)
            putExtra("ADDRESS", currentAddress)
            putExtra("USER_NICKNAME", receivedNickname)
        }
        startActivity(newIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onPause() {
        super.onPause()
        mapView.pause()
    }
}