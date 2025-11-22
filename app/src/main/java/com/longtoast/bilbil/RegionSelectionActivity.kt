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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegionSelectionActivity : AppCompatActivity() {

    data class RegionLeaf(val name: String, val latitude: Double, val longitude: Double)

    private val regionData: Map<String, Map<String, List<RegionLeaf>>> = mapOf(
        "서울특별시" to mapOf(
            "강서구" to listOf(
                RegionLeaf("등촌동", 37.5515, 126.8637),
                RegionLeaf("화곡동", 37.5415, 126.8407),
                RegionLeaf("마곡동", 37.5600, 126.8251)
            ),
            "양천구" to listOf(
                RegionLeaf("목동", 37.5263, 126.8643),
                RegionLeaf("신월동", 37.5245, 126.8343),
                RegionLeaf("신정동", 37.5188, 126.8569)
            ),
            "동작구" to listOf(
                RegionLeaf("사당동", 37.4765, 126.9816),
                RegionLeaf("상도동", 37.5057, 126.9499),
                RegionLeaf("흑석동", 37.5095, 126.9637)
            )
        ),
        "경기도" to mapOf(
            "고양시" to listOf(
                RegionLeaf("일산동", 37.6614, 126.7684),
                RegionLeaf("식사동", 37.6807, 126.8116),
                RegionLeaf("대화동", 37.6762, 126.7476)
            ),
            "성남시" to listOf(
                RegionLeaf("분당동", 37.3820, 127.1187),
                RegionLeaf("정자동", 37.3678, 127.1084),
                RegionLeaf("위례동", 37.4742, 127.1437)
            ),
            "부천시" to listOf(
                RegionLeaf("중동", 37.5035, 126.7614),
                RegionLeaf("상동", 37.5054, 126.7523),
                RegionLeaf("송내동", 37.4879, 126.7535)
            )
        ),
        "강원도" to mapOf(
            "춘천시" to listOf(
                RegionLeaf("석사동", 37.8619, 127.7341),
                RegionLeaf("퇴계동", 37.8684, 127.7219),
                RegionLeaf("동내면", 37.8241, 127.7888)
            ),
            "원주시" to listOf(
                RegionLeaf("단계동", 37.3534, 127.9467),
                RegionLeaf("무실동", 37.3356, 127.9304),
                RegionLeaf("봉산동", 37.3389, 127.9559)
            ),
            "강릉시" to listOf(
                RegionLeaf("교동", 37.7554, 128.8961),
                RegionLeaf("포남동", 37.7721, 128.9186),
                RegionLeaf("홍제동", 37.7678, 128.8793)
            )
        )
    )

    private lateinit var provinceList: RecyclerView
    private lateinit var cityList: RecyclerView
    private lateinit var townList: RecyclerView
    private lateinit var selectedSummary: TextView
    private lateinit var confirmButton: Button

    private var selectedProvince: String? = null
    private var selectedCity: String? = null
    private var selectedTown: RegionLeaf? = null

    private var userId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)

        userId = intent.getIntExtra("USER_ID", 0)

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
            submitLocation(address, town)
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
        val summary = listOfNotNull(selectedProvince, selectedCity, selectedTown?.name).joinToString(" → ")
        selectedSummary.text = if (summary.isEmpty()) "대여 가능 지역을 선택해주세요" else summary
        confirmButton.isEnabled = selectedTown != null
    }

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
