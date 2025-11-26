package com.longtoast.bilbil

import android.content.Intent
import android.os.Bundle
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

    private lateinit var provinceList: RecyclerView
    private lateinit var cityList: RecyclerView
    private lateinit var townList: RecyclerView
    private lateinit var selectedSummary: TextView
    private lateinit var confirmButton: Button

    // JSON 구조 파싱용
    private lateinit var rawRegionData: Map<String, Map<String, List<RawTown>>>

    // 실제 앱 내부에서 사용할 구조 (위경도 포함)
    private lateinit var regionData: Map<String, Map<String, List<RegionLeaf>>>

    // JSON 내부 town 구조 : 이름만 존재
    data class RawTown(val name: String)

    // 실제 사용하는 구조 : 위경도 포함 (0.0 으로 채움)
    data class RegionLeaf(val name: String, val latitude: Double, val longitude: Double)

    private var selectedProvince: String? = null
    private var selectedCity: String? = null
    private var selectedTown: RegionLeaf? = null

    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)

        userId = intent.getIntExtra("USER_ID", 0)

        initViews()
        loadJsonData()
        setupLists()
    }

    private fun initViews() {
        provinceList = findViewById(R.id.list_province)
        cityList = findViewById(R.id.list_city)
        townList = findViewById(R.id.list_town)
        selectedSummary = findViewById(R.id.text_selection_summary)
        confirmButton = findViewById(R.id.button_region_confirm)
    }

    // --------------------------------------------------------------------
    // ⭐ JSON 로드 후 → latitude/longitude 없는 town에 0.0 값 자동 부여
    // --------------------------------------------------------------------
    private fun loadJsonData() {
        val inputStream = resources.openRawResource(R.raw.full_regions_cleaned)
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        // name만 있는 JSON 먼저 파싱
        val type = object : TypeToken<Map<String, Map<String, List<RawTown>>>>() {}.type
        rawRegionData = Gson().fromJson(jsonString, type)

        // 위경도 = 0.0 으로 자동 채운 최종 구조 만들기
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

        // 시/도
        val provinceAdapter = RegionOptionAdapter(regionData.keys.toList()) { province ->
            selectedProvince = province
            selectedCity = null
            selectedTown = null
            updateCityOptions(province)
            updateSummary()
        }
        provinceList.adapter = provinceAdapter

        confirmButton.setOnClickListener {
            if (selectedProvince == null || selectedCity == null || selectedTown == null) {
                Toast.makeText(this, "도/시/동을 모두 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val town = selectedTown ?: return@setOnClickListener
            val address = "$selectedProvince $selectedCity ${town.name}"

            submitLocation(address, town)
        }
    }

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

    private fun updateSummary() {
        val summary = listOfNotNull(selectedProvince, selectedCity, selectedTown?.name)
            .joinToString(" → ")

        selectedSummary.text =
            if (summary.isEmpty()) "대여 가능 지역을 선택해주세요"
            else summary

        confirmButton.isEnabled = selectedTown != null
    }

    // --------------------------------------------------------------------
    // ⭐ DTO는 그대로 사용 → latitude, longitude = 0.0 값 그대로 전달
    // --------------------------------------------------------------------
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
                            latitude = town.latitude,   // 0.0 자동 입력
                            longitude = town.longitude, // 0.0 자동 입력
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
                Toast.makeText(this@RegionSelectionActivity, "지역 저장에 실패했습니다", Toast.LENGTH_LONG).show()
                return@launch
            }

            val resultIntent = Intent().apply {
                putExtra("FINAL_ADDRESS", address)
                putExtra("FINAL_LATITUDE", town.latitude)
                putExtra("FINAL_LONGITUDE", town.longitude)
            }

            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
