// com.longtoast.bilbil.SettingMapActivity.kt (전체)
package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.LocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingMapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var kakaoMap: KakaoMap? = null

    private lateinit var editSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnCurrentLocation: Button
    private lateinit var btnConfirm: Button
    private lateinit var txtSelectedAddress: TextView
    private lateinit var recyclerSearch: RecyclerView
    private lateinit var searchAdapter: SearchAdapter

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLat = 37.50115001650065
    private var currentLng = 126.8675615713012
    private var currentAddress: String = ""

    private val KAKAO_REST_API_KEY = "9f3f18b8416277279d74a206762f21b1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_map)

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initRecycler()
        setupMap()
        setupListeners()

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        editSearch = findViewById(R.id.editSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation)
        btnConfirm = findViewById(R.id.btnConfirm)
        txtSelectedAddress = findViewById(R.id.txtSelectedAddress)
        recyclerSearch = findViewById(R.id.recycler_search_result)
    }

    private fun initRecycler() {
        recyclerSearch.layoutManager = LinearLayoutManager(this)

        searchAdapter = SearchAdapter(emptyList()) { item ->
            currentLat = item.latitude
            currentLng = item.longitude
            currentAddress = item.address

            moveCameraTo(currentLat, currentLng)

            txtSelectedAddress.text = currentAddress
            editSearch.setText(currentAddress)

            recyclerSearch.visibility = RecyclerView.GONE
        }

        recyclerSearch.adapter = searchAdapter
    }

    private fun setupMap() {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(error: Exception?) {
                    Toast.makeText(this@SettingMapActivity, "지도 로드 오류", Toast.LENGTH_SHORT).show()
                }
            },

            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    checkLocationPermission()

                    setupCameraMoveEndListener()
                }
            }
        )
    }

    /** ★★★ 카메라 이동 종료 시점에 정확하게 중심 좌표 가져오기 ★★★ */
    private fun setupCameraMoveEndListener() {
        kakaoMap?.setOnCameraMoveEndListener { map, camPos, reason ->

            val center = camPos.position

            currentLat = center.latitude
            currentLng = center.longitude

            Log.d("MAP_CENTER", "Camera End → lat=$currentLat, lng=$currentLng")

            loadAddress(currentLat, currentLng)
        }
    }

    private fun setupListeners() {
        btnSearch.setOnClickListener {
            val q = editSearch.text.toString().trim()
            if (q.isNotEmpty()) searchAddress(q)
        }

        editSearch.setOnClickListener {
            editSearch.text.clear()
        }

        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = editSearch.text.toString().trim()
                if (q.isNotEmpty()) searchAddress(q)
                true
            } else false
        }

        btnCurrentLocation.setOnClickListener { getCurrentLocation() }

        btnConfirm.setOnClickListener {
            val userId = intent.getIntExtra("USER_ID", -1)
            val serviceToken = intent.getStringExtra("SERVICE_TOKEN")

            // isInitialSetup은 MainActivity에서 초기 설정으로 왔는지 확인하는 플래그 (여기에선 사용 안 함)
            val isInitialSetup = intent.hasExtra("USER_NICKNAME")
            val nickname = intent.getStringExtra("USER_NICKNAME")

            if (userId == -1) {
                Toast.makeText(this, "User ID 오류", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (serviceToken.isNullOrEmpty()) {
                Toast.makeText(this, "인증 토큰 오류", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val finalAddress = editSearch.text.toString().trim()
                if (finalAddress.isEmpty()) {
                    Toast.makeText(this@SettingMapActivity, "유효한 주소를 설정해주세요.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                currentAddress = finalAddress

                // 1. 서버에 위치 저장 (비동기)
                val ok = sendLocationToServer(userId, currentLat, currentLng, currentAddress)
                if (!ok) {
                    Toast.makeText(this@SettingMapActivity, "서버 저장 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(this@SettingMapActivity, "위치 저장 완료!", Toast.LENGTH_SHORT).show()

                // 2. 🚨 [핵심 수정] 호출한 Activity/Fragment로 결과 반환
                val resultIntent = Intent().apply {
                    putExtra("FINAL_ADDRESS", currentAddress)
                    putExtra("FINAL_LATITUDE", currentLat)
                    putExtra("FINAL_LONGITUDE", currentLng)
                }
                setResult(RESULT_OK, resultIntent)

                // 3. 🚨 [수정] 초기 설정(MainActivity)에서 온 경우에만 홈 화면으로 이동
                if (isInitialSetup) {
                    // MainActivity -> SettingMapActivity -> SettingProfileActivity를 거쳐왔다고 가정
                    // 여기서는 SettingProfileActivity로 이동해야 함 (수정 전 코드와 동일하게 처리)
                    // 현재 로직이 SettingMapActivity 다음에 바로 HomeHostActivity로 이동하므로, 이 로직을 유지합니다.

                    // 💡 [정리] 이 로직은 초기 설정(MainActivity -> SettingMapActivity)에서만 실행되어야 합니다.
                    // 현재는 SettingProfileActivity가 이전에 실행되도록 설정되어 있지 않으므로 HomeHostActivity로 바로 이동합니다.
                    val intent = Intent(this@SettingMapActivity, HomeHostActivity::class.java)
                    startActivity(intent)
                }

                finish() // Activity 종료
            }
        }
    }

    // ... (checkLocationPermission, getCurrentLocation, moveCameraTo, loadAddress, reverseGeocode, searchAddress, searchKeyword, sendLocationToServer 유지) ...
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (fine) getCurrentLocation()
        }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { loc ->
            loc?.let {
                currentLat = it.latitude
                currentLng = it.longitude

                moveCameraTo(currentLat, currentLng)
                loadAddress(currentLat, currentLng)
            }
        }
    }

    private fun moveCameraTo(lat: Double, lng: Double) {
        kakaoMap?.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(lat, lng)
            )
        )
    }

    private fun loadAddress(lat: Double, lng: Double) {
        lifecycleScope.launch {
            val addr = withContext(Dispatchers.IO) { reverseGeocode(lat, lng) }

            currentAddress = addr ?: "위도: $lat, 경도: $lng"

            txtSelectedAddress.text = currentAddress
            editSearch.setText(currentAddress)
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        val url =
            URL("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat")

        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_API_KEY")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                val docs = JSONObject(body).getJSONArray("documents")

                if (docs.length() > 0)
                    docs.getJSONObject(0)
                        .getJSONObject("address")
                        .getString("address_name")
                else null
            } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun searchAddress(q: String) {
        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { searchKeyword(q) }

                if (list.isEmpty()) {
                    Toast.makeText(this@SettingMapActivity, "검색 결과 없음", Toast.LENGTH_SHORT).show()
                    recyclerSearch.visibility = RecyclerView.GONE
                } else {
                    recyclerSearch.visibility = RecyclerView.VISIBLE
                    searchAdapter.updateList(list)
                }
            } catch (e: Exception) {
                Log.e("MapSearch", "searchAddress error", e)
            }
        }
    }

    private fun searchKeyword(q: String): List<SearchItem> {
        val encoded = URLEncoder.encode(q, "UTF-8")

        val url =
            URL("https://dapi.kakao.com/v2/local/search/keyword.json?query=$encoded")

        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "KakaoAK $KAKAO_REST_API_KEY")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONObject(body).getJSONArray("documents")

                val out = mutableListOf<SearchItem>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)

                    out.add(
                        SearchItem(
                            o.getString("place_name"),
                            o.getString("address_name"),
                            o.getString("y").toDouble(),
                            o.getString("x").toDouble()
                        )
                    )
                }

                out
            } else emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun sendLocationToServer(
        userId: Int,
        lat: Double,
        lng: Double,
        address: String
    ): Boolean {

        val body = LocationRequest(userId, lat, lng, address)

        return try {
            val response = RetrofitClient.getApiService().sendLocation(body)

            Log.d("LOCATION_SAVE", "응답 코드 = ${response.code()}, 성공 여부 = ${response.isSuccessful}")
            if (!response.isSuccessful) {
                Log.e("LOCATION_SAVE", "에러 바디 = ${response.errorBody()?.string()}")
            }

            response.isSuccessful
        } catch (e: Exception) {
            Log.e("LOCATION_SAVE", "예외 발생", e)
            false
        }
    }
}