package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.LocationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegionSelectionActivity : AppCompatActivity() {

    companion object {
        const val MODE_PROFILE = "PROFILE"   // 사용자 활동 지역 설정
        const val MODE_FILTER = "FILTER"     // 리스트 필터용

        const val EXTRA_MODE = "MODE"
        const val EXTRA_USER_ID = "USER_ID"

        const val EXTRA_ADDRESS = "FINAL_ADDRESS"
        const val EXTRA_LATITUDE = "FINAL_LATITUDE"
        const val EXTRA_LONGITUDE = "FINAL_LONGITUDE"
        const val EXTRA_PROVINCE = "FINAL_PROVINCE"
        const val EXTRA_CITY = "FINAL_CITY"
        const val EXTRA_TOWN = "FINAL_TOWN"
    }

    private lateinit var provinceList: RecyclerView
    private lateinit var cityList: RecyclerView
    private lateinit var townList: RecyclerView
    private lateinit var selectedSummary: TextView
    private lateinit var confirmButton: Button
    private lateinit var titleText: TextView
    private lateinit var subTitleText: TextView

    private lateinit var mode: String

    private lateinit var rawRegionData: Map<String, Map<String, List<RawTown>>>
    private lateinit var regionData: Map<String, Map<String, List<RegionLeaf>>>

    data class RawTown(val name: String)

    data class RegionLeaf(val name: String, val latitude: Double, val longitude: Double)

    private var selectedProvince: String? = null
    private var selectedCity: String? = null
    private var selectedTown: RegionLeaf? = null

    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PROFILE
        userId = intent.getIntExtra(EXTRA_USER_ID, 0)

        initViews()
        setupUiByMode()
        loadJsonData()
        setupLists()
    }

    private fun initViews() {
        provinceList = findViewById(R.id.list_province)
        cityList = findViewById(R.id.list_city)
        townList = findViewById(R.id.list_town)
        selectedSummary = findViewById(R.id.text_selection_summary)
        confirmButton = findViewById(R.id.button_region_confirm)
        titleText = findViewById(R.id.text_region_title)
        subTitleText = findViewById(R.id.text_region_subtitle)
    }

    // 🔹 모드에 따라 UI / 버튼 텍스트 / 동 리스트 표시 여부 설정
    private fun setupUiByMode() {
        if (mode == MODE_FILTER) {
            titleText.text = "필터할 지역을 선택해주세요"
            subTitleText.text = "도 → 시/구를 선택해서 게시글을 필터링합니다."
            confirmButton.text = "이 지역으로 필터"

            // ⛔ 필터 모드에서는 동 리스트 숨김 (도 + 시/구만 사용)
            townList.visibility = View.GONE

            // 아무것도 안 선택해도 "전체"로 필터 가능해야 하므로 기본 활성화
            confirmButton.isEnabled = true
        } else {
            titleText.text = "대여 가능 지역을 선택해주세요"
            subTitleText.text = "도 → 시/구 → 동 순서로 맞춤 지역을 설정하세요."
            confirmButton.text = "선택 완료"

            // 프로필 모드에서는 동까지 선택해야 활성화
            townList.visibility = View.VISIBLE
            confirmButton.isEnabled = false
        }
    }

    private fun loadJsonData() {
        val inputStream = resources.openRawResource(R.raw.full_regions_cleaned)
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        val type = object : TypeToken<Map<String, Map<String, List<RawTown>>>>() {}.type
        rawRegionData = Gson().fromJson(jsonString, type)

        regionData = rawRegionData.mapValues { provinceEntry ->
            provinceEntry.value.mapValues { cityEntry ->
                cityEntry.value.map { rawTown ->
                    RegionLeaf(
                        name = rawTown.name,
                        latitude = 0.0,
                        longitude = 0.0
                    )
                }
            }
        }
    }

    private fun setupLists() {
        provinceList.layoutManager = LinearLayoutManager(this)
        cityList.layoutManager = LinearLayoutManager(this)
        townList.layoutManager = LinearLayoutManager(this)

        val provinceAdapter = RegionOptionAdapter(regionData.keys.toList()) { province ->
            selectedProvince = province
            selectedCity = null
            selectedTown = null
            updateCityOptions(province)
            updateSummary()
        }
        provinceList.adapter = provinceAdapter

        // 🔹 모드에 따라 확인 버튼 동작 변경
        if (mode == MODE_FILTER) {
            confirmButton.setOnClickListener { onConfirmFilterMode() }
        } else {
            confirmButton.setOnClickListener { onConfirmProfileMode() }
        }
    }

    // ---------------------------------------------------------
    // FILTER 모드: 도/시만 사용, 선택 안 해도 "전체"로 필터
    // ---------------------------------------------------------
    private fun onConfirmFilterMode() {
        // 1) 아무 것도 선택 안 한 상태 → "필터 없음"으로 리턴
        if (selectedProvince == null && selectedCity == null) {
            val resultIntent = Intent()
            // EXTRA_ADDRESS 안 넣음 → 호출 측에서 null이면 "지역 전체"
            setResult(RESULT_OK, resultIntent)
            finish()
            return
        }

        // 2) 도만 선택한 상태 → 시/구 선택 요청
        if (selectedProvince != null && selectedCity == null) {
            Toast.makeText(this, "시/구를 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        // 3) 도 + 시/구 선택한 경우 → "서울특별시 양천구" 이런 형태로만 전달 (동 없음)
        val address = "$selectedProvince $selectedCity"

        val resultIntent = Intent().apply {
            putExtra(EXTRA_ADDRESS, address)
            putExtra(EXTRA_PROVINCE, selectedProvince)
            putExtra(EXTRA_CITY, selectedCity)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // ---------------------------------------------------------
    // PROFILE 모드: 도/시/동까지 선택해서 서버에 저장
    // ---------------------------------------------------------
    private fun onConfirmProfileMode() {
        if (selectedProvince == null || selectedCity == null || selectedTown == null) {
            Toast.makeText(this, "도/시/동을 모두 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val town = selectedTown ?: return
        val address = "$selectedProvince $selectedCity ${town.name}"

        submitLocation(address, town)
    }

    // ---------------------------------------------------------
    // 리스트 갱신
    // ---------------------------------------------------------
    private fun updateCityOptions(province: String) {
        val cities = regionData[province]?.keys?.toList() ?: emptyList()

        cityList.adapter = RegionOptionAdapter(cities) { city ->
            selectedCity = city
            selectedTown = null
            updateTownOptions(province, city)
            updateSummary()
        }

        townList.adapter = RegionOptionAdapter(emptyList()) {}
    }

    private fun updateTownOptions(province: String, city: String) {
        val towns = regionData[province]?.get(city) ?: emptyList()

        townList.adapter = RegionOptionAdapter(towns.map { it.name }) { townName ->
            selectedTown = towns.firstOrNull { it.name == townName }
            updateSummary()
        }
    }

    // ---------------------------------------------------------
    // 선택 요약 텍스트 + 버튼 활성화
    // ---------------------------------------------------------
    private fun updateSummary() {
        if (mode == MODE_FILTER) {
            // 🔹 필터 모드: 도 → 시/구까지만 요약에 표시
            val summary = listOfNotNull(selectedProvince, selectedCity)
                .joinToString(" → ")

            selectedSummary.text =
                if (summary.isEmpty())
                    "지역을 선택하지 않으면 전체 지역이 검색됩니다"
                else summary

            // 아무것도 안 골라도 "전체"로 필터 가능해야 하므로 계속 활성화
            confirmButton.isEnabled = true

        } else {
            // 🔹 프로필 모드: 도 → 시/구 → 동
            val summary = listOfNotNull(selectedProvince, selectedCity, selectedTown?.name)
                .joinToString(" → ")

            selectedSummary.text =
                if (summary.isEmpty()) "대여 가능 지역을 선택해주세요"
                else summary

            // 동까지 선택했을 때만 완료 버튼 활성화
            confirmButton.isEnabled = selectedTown != null
        }
    }

    // ---------------------------------------------------------
    // PROFILE 모드에서만 사용: 서버에 위치 저장
    // ---------------------------------------------------------
    private fun submitLocation(address: String, town: RegionLeaf) {
        if (userId == 0) {
            Toast.makeText(this, "사용자 정보를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        confirmButton.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val response = RetrofitClient.getApiService().sendLocation(
                        LocationRequest(
                            userId = userId,
                            latitude = town.latitude,
                            longitude = town.longitude,
                            address = address
                        )
                    )
                    response.isSuccessful
                } catch (e: Exception) {
                    false
                }
            }

            confirmButton.isEnabled = true

            if (!success) {
                Toast.makeText(
                    this@RegionSelectionActivity,
                    "지역 저장에 실패했습니다",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val resultIntent = Intent().apply {
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_LATITUDE, town.latitude)
                putExtra(EXTRA_LONGITUDE, town.longitude)
                putExtra(EXTRA_PROVINCE, selectedProvince)
                putExtra(EXTRA_CITY, selectedCity)
                putExtra(EXTRA_TOWN, town.name)
            }

            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
