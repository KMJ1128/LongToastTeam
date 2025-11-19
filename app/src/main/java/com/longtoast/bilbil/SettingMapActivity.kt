package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
// ... (기존 import 유지)
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
import java.lang.reflect.Method
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

    private val LOCATION_PERMISSION = 1000

    private val KAKAO_REST_API_KEY = "9f3f18b8416277279d74a206762f21b1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_map)

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initRecycler()
        setupMap()
        setupListeners()

        // 권한 요청
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
            currentAddress = item.name

            moveCameraTo(currentLat, currentLng)
            txtSelectedAddress.text = currentAddress
            recyclerSearch.visibility = RecyclerView.GONE
        }
        recyclerSearch.adapter = searchAdapter
    }

    // 지도 로딩
    private fun setupMap() {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(error: Exception?) {
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
                    checkLocationPermission()
                    activateCameraTouchListener()
                }
            }
        )
    }

    // 지도 터치 후 손 뗄 때 중심 좌표 가져오기
    private fun activateCameraTouchListener() {
        mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {

                kakaoMap?.let { map ->
                    val camPos = map.cameraPosition ?: return@let

                    // 1) 카메라 중심 lat/lng 추출
                    val latLngPair = tryExtractLatLngFromCameraPosition(camPos)
                    if (latLngPair != null) {
                        currentLat = latLngPair.first
                        currentLng = latLngPair.second
                        loadAddress(currentLat, currentLng)
                    } else {
                        // 2) fallback
                        try {
                            val method: Method? = map::class.java.methods.firstOrNull {
                                it.name.equals("getCenter", true) || it.name.equals("center", true)
                            }
                            val center = method?.invoke(map)
                            val extracted = center?.let { extractLatLngFromLatLngLike(it) }
                            if (extracted != null) {
                                currentLat = extracted.first
                                currentLng = extracted.second
                                loadAddress(currentLat, currentLng)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            false
        }
    }

    private fun setupListeners() {

        // 검색 버튼
        btnSearch.setOnClickListener {
            val q = editSearch.text.toString().trim()
            if (q.isNotEmpty()) searchAddress(q)
        }

        // 키보드 검색 버튼
        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = editSearch.text.toString().trim()
                if (q.isNotEmpty()) searchAddress(q)
                true
            } else false
        }

        // 현재 위치 버튼
        btnCurrentLocation.setOnClickListener { getCurrentLocation() }

        // 확인 버튼
        btnConfirm.setOnClickListener {
            val userId = intent.getIntExtra("USER_ID", -1)
            val serviceToken = intent.getStringExtra("SERVICE_TOKEN")

            // 🚨 초기 설정 경로 확인 (MainActivity에서 USER_NICKNAME Extra가 전달되었는지 여부로 판단)
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

            // 🚨 [핵심 수정] 서버 통신 전에 토큰을 저장하여 RetrofitClient가 사용하도록 보장
            if (serviceToken != null) {
                AuthTokenManager.saveToken(serviceToken)
            }

            lifecycleScope.launch {

                val ok = sendLocationToServer(userId, currentLat, currentLng, currentAddress)

                if (!ok) {
                    Toast.makeText(this@SettingMapActivity, "서버 저장 실패", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Toast.makeText(this@SettingMapActivity, "위치 저장 완료!", Toast.LENGTH_SHORT).show()

                // 1. 초기 설정 모드 (MainActivity를 통해 진입했고, 닉네임 Extra가 존재함)
                if (isInitialSetup) {

                    // 1-1. 닉네임이 이미 있는 경우 (DB에 닉네임이 있음) -> 홈으로 이동
                    if (!nickname.isNullOrEmpty()) {
                        Log.d("SettingMap", "초기 설정 경로 (닉네임 존재): 홈으로 바로 이동.")

                        AuthTokenManager.saveUserId(userId)

                        val intent = Intent(this@SettingMapActivity, HomeHostActivity::class.java)
                        startActivity(intent)
                        finish()
                        return@launch
                    }

                    // 1-2. 닉네임이 없는 경우 (DB에 닉네임이 없음) -> 프로필 설정으로 이동
                    Log.d("SettingMap", "초기 설정 경로 (닉네임 없음): 프로필 설정으로 이동.")
                    val intent = Intent(this@SettingMapActivity, SettingProfileActivity::class.java).apply {
                        putExtra("USER_ID", userId)
                        putExtra("SERVICE_TOKEN", serviceToken)
                        putExtra("LATITUDE", currentLat)
                        putExtra("LONGITUDE", currentLng)
                        putExtra("ADDRESS", currentAddress)
                    }
                    startActivity(intent)
                    finish()
                    return@launch
                }

                // 2. 기존 회원 위치 업데이트 경로 (HomeFragment를 통해 진입했고, 닉네임 Extra가 없음)
                //    -> 위치 업데이트만 완료했으므로 무조건 홈으로 이동.
                else {
                    Log.d("SettingMap", "기존 회원 위치 업데이트 경로: 홈으로 이동.")
                    AuthTokenManager.saveUserId(userId)

                    val intent = Intent(this@SettingMapActivity, HomeHostActivity::class.java)
                    startActivity(intent)
                    finish()
                    return@launch
                }
            }
        }
    }

    // 권한 launcher
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
        ).addOnSuccessListener { loc: Location? ->
            loc?.let {
                currentLat = it.latitude
                currentLng = it.longitude
                moveCameraTo(currentLat, currentLng)
                loadAddress(currentLat, currentLng)
            }
        }
    }

    // 지도 카메라 이동
    private fun moveCameraTo(lat: Double, lng: Double) {
        kakaoMap?.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(lat, lng)
            )
        )
    }

    // 중심 좌표 reverse geocode
    private fun loadAddress(lat: Double, lng: Double) {
        lifecycleScope.launch {
            val addr = withContext(Dispatchers.IO) { reverseGeocode(lat, lng) }
            currentAddress = addr ?: "위도: $lat, 경도: $lng"
            txtSelectedAddress.text = currentAddress
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
                    docs.getJSONObject(0).getJSONObject("address").getString("address_name")
                else null
            } else null
        } finally {
            conn.disconnect()
        }
    }

    // 키워드 검색
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

    override fun onResume() { super.onResume(); mapView.resume() }
    override fun onPause() { super.onPause(); mapView.pause() }

    // 카메라 중심 좌표 reflection 추출
    private fun tryExtractLatLngFromCameraPosition(camPos: Any): Pair<Double, Double>? {
        val getters = listOf(
            "getLatLng", "latLng", "getTarget", "target",
            "getCenter", "center"
        )

        for (name in getters) {
            try {
                val m = camPos::class.java.getMethod(name)
                val obj = m.invoke(camPos) ?: continue
                val extracted = extractLatLngFromLatLngLike(obj)
                if (extracted != null) return extracted
            } catch (_: Exception) {}
        }

        // fallback: cameraPosition에서 lat/lng 직접 탐색
        try {
            val mLat = camPos::class.java.getMethod("getLatitude")
            val mLng = camPos::class.java.getMethod("getLongitude")
            val lat = (mLat.invoke(camPos) as? Number)?.toDouble()
            val lng = (mLng.invoke(camPos) as? Number)?.toDouble()
            if (lat != null && lng != null) return Pair(lat, lng)
        } catch (_: Exception) {}

        return null
    }

    private fun extractLatLngFromLatLngLike(obj: Any): Pair<Double, Double>? {
        val cls = obj::class.java

        val latNames = listOf("getLatitude", "getLat", "latitude", "lat")
        val lngNames = listOf("getLongitude", "getLng", "longitude", "lng")

        var lat: Double? = null
        var lng: Double? = null

        for (n in latNames) {
            try {
                val m = cls.getMethod(n)
                val v = m.invoke(obj)
                if (v is Number) { lat = v.toDouble(); break }
            } catch (_: Exception) {}
        }

        for (n in lngNames) {
            try {
                val m = cls.getMethod(n)
                val v = m.invoke(obj)
                if (v is Number) { lng = v.toDouble(); break }
            } catch (_: Exception) {}
        }

        return if (lat != null && lng != null) Pair(lat, lng) else null
    }

    private suspend fun sendLocationToServer(userId: Int, lat: Double, lng: Double, address: String): Boolean {
        return try {
            val body = LocationRequest(userId, lat, lng, address)
            val response = RetrofitClient.getApiService().sendLocation(body)

            // API 응답 코드 및 에러 로깅 보강
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Log.e("SEND_LOCATION", "API 요청 실패! 코드: ${response.code()}, 에러: $errorBody")
            }

            response.isSuccessful
        } catch (e: Exception) {
            Log.e("SEND_LOCATION", "네트워크 통신 오류", e)
            false
        }
    }
}