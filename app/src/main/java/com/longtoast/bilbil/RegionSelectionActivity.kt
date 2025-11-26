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
import com.longtoast.bilbil.api.RetrofitClient
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.Town
import com.longtoast.bilbil.util.RegionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegionSelectionActivity : AppCompatActivity() {

    private lateinit var regionData: Map<String, Map<String, List<Town>>>

    private lateinit var provinceList: RecyclerView
    private lateinit var cityList: RecyclerView
    private lateinit var townList: RecyclerView
    private lateinit var selectedSummary: TextView
    private lateinit var confirmButton: Button

    private var selectedProvince: String? = null
    private var selectedCity: String? = null
    private var selectedTown: Town? = null

    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)

        userId = intent.getIntExtra("USER_ID", 0)

        // JSON 파일에서 지역 데이터 로드
        regionData = RegionLoader.loadRegions(this)

        initViews()
        setupLists()
    }

    private fun initViews() {
        provinceList = findViewById(R.id.list_province)
        cityList = findViewById(R.id.list_city)
        townList = findViewById(R.id.list_town)
        selectedSummary = findViewById(R.id.text_selection_summary)
        confirmButton = findViewById(R.id.button_region_confirm)
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

        confirmButton.setOnClickListener {
            if (selectedProvince == null || selectedCity == null || selectedTown == null) {
                Toast.makeText(this, "도/시/동을 모두 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val town = selectedTown ?: return@setOnClickListener
            val address = "${selectedProvince} ${selectedCity} ${town.name}"

            // 위도/경도는 기본값만 사용
            submitLocation(address, 37.5665, 126.9780)
        }
    }

    private fun updateCityOptions(province: String) {
        val cities = regionData[province]?.keys?.toList() ?: emptyList()
        val adapter = RegionOptionAdapter(cities) { city ->
            selectedCity = city
            selectedTown = null
            updateTownOptions(province, city)
            updateSummary()
        }
        cityList.adapter = adapter
        townList.adapter = RegionOptionAdapter(emptyList()) {}
    }

    private fun updateTownOptions(province: String, city: String) {
        val towns = regionData[province]?.get(city) ?: emptyList()
        val adapter = RegionOptionAdapter(towns.map { it.name }) { townName ->
            selectedTown = towns.firstOrNull { it.name == townName }
            updateSummary()
        }
        townList.adapter = adapter
    }

    private fun updateSummary() {
        val summary = listOfNotNull(selectedProvince, selectedCity, selectedTown?.name)
            .joinToString(" → ")
        selectedSummary.text = if (summary.isEmpty()) {
            "대여 가능 지역을 선택해주세요"
        } else {
            summary
        }
        confirmButton.isEnabled = selectedTown != null
    }

    private fun submitLocation(address: String, latitude: Double, longitude: Double) {
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
                            latitude = latitude,
                            longitude = longitude,
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
                putExtra("FINAL_ADDRESS", address)
                putExtra("FINAL_LATITUDE", latitude)
                putExtra("FINAL_LONGITUDE", longitude)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}