package com.longtoast.bilbil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.LocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// 네이버 지도 import
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.CameraUpdate
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.Marker


class SettingMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var naverMap: NaverMap? = null
    private var marker: Marker? = null

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

    // 검색은 카카오 REST API 그대로 사용
    private val KAKAO_REST_API_KEY = "9f3f18b8416277279d74a206762f21b1"

    // 🆕 추가: 프로필 수정 모드인지 구분
    private var isProfileEditMode: Boolean = false
    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting_map)

        userId = intent.getIntExtra("USER_ID", 0)
        isProfileEditMode = intent.getBooleanExtra("IS_PROFILE_EDIT", false)  // 🆕 추가

        initViews()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initRecycler()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        setupListeners()

        // 위치 권한 요청
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
            setMarker(currentLat, currentLng)

            txtSelectedAddress.text = currentAddress
            editSearch.setText(currentAddress)

            recyclerSearch.visibility = RecyclerView.GONE
        }

        recyclerSearch.adapter = searchAdapter
    }

    /** 네이버 지도 준비 완료 */
    override fun onMapReady(map: NaverMap) {
        this.naverMap = map

        moveCameraTo(currentLat, currentLng)
        setMarker(currentLat, currentLng)

        // 중심 이동 끝날 때 이벤트
        map.addOnCameraChangeListener { _, _ -> }
        map.addOnCameraIdleListener {
            val target = map.cameraPosition.target

            currentLat = target.latitude
            currentLng = target.longitude

            setMarker(currentLat, currentLng)
            loadAddress(currentLat, currentLng)
        }

        checkLocationPermission()
    }

    /** 카메라 이동 */
    private fun moveCameraTo(lat: Double, lng: Double) {
        val cameraUpdate = CameraUpdate.scrollTo(LatLng(lat, lng))
        naverMap?.moveCamera(cameraUpdate)
    }

    /** 지도 마커 찍기 */
    private fun setMarker(lat: Double, lng: Double) {
        if (marker == null) {
            marker = Marker()
        }
        marker!!.position = LatLng(lat, lng)
        marker!!.map = naverMap
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

            if (userId == -1 || serviceToken.isNullOrEmpty()) {
                Toast.makeText(this, "인증 오류", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val address = editSearch.text.toString().trim()
                if (address.isEmpty()) {
                    Toast.makeText(this@SettingMapActivity, "주소를 선택해주세요.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                currentAddress = address

                // 🆕 수정: 프로필 수정 모드일 때만 서버에 저장
                val ok = if (isProfileEditMode) {
                    sendLocationToServer(userId, currentLat, currentLng, currentAddress)
                } else {
                    true  // 게시글 작성 모드는 서버 저장 안 함
                }

                if (ok) {
                    val resultIntent = Intent().apply {
                        putExtra("FINAL_ADDRESS", currentAddress)
                        putExtra("FINAL_LATITUDE", currentLat)
                        putExtra("FINAL_LONGITUDE", currentLng)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    Toast.makeText(this@SettingMapActivity, "서버 저장 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** ======================
     *   권한 및 위치 처리
    ======================= */

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                getCurrentLocation()
            }
        }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
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
                setMarker(currentLat, currentLng)
                loadAddress(currentLat, currentLng)
            }
        }
    }


    /** ======================
     *    주소 검색 / 역검색
    ======================= */

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
                    recyclerSearch.visibility = RecyclerView.GONE
                    Toast.makeText(this@SettingMapActivity, "검색 결과 없음", Toast.LENGTH_SHORT).show()
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


    /** ======================
     *   서버에 위치 저장
    ======================= */

    private suspend fun sendLocationToServer(
        userId: Int,
        lat: Double,
        lng: Double,
        address: String
    ): Boolean {

        val body = LocationRequest(userId, lat, lng, address)

        return try {
            val response = RetrofitClient.getApiService().sendLocation(body)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }


    /** ======================
     *    네이버 맵 라이프사이클
    ======================= */

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
}