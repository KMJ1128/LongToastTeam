package com.longtoast.bilbil.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.longtoast.bilbil.dto.Town
import java.io.IOException

object RegionLoader {

    private var cachedData: Map<String, Map<String, List<Town>>>? = null

    fun loadRegions(context: Context): Map<String, Map<String, List<Town>>> {
        if (cachedData != null) {
            return cachedData!!
        }

        try {
            val jsonString = context.assets.open("full_regions_cleaned.json")
                .bufferedReader()
                .use { it.readText() }

            val gson = Gson()
            val type = object : TypeToken<Map<String, Map<String, List<Town>>>>() {}.type
            cachedData = gson.fromJson(jsonString, type)

            return cachedData!!

        } catch (e: IOException) {
            e.printStackTrace()
            return emptyMap()
        }
    }
}