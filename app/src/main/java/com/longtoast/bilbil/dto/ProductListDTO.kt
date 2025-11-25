package com.longtoast.bilbil.dto

import com.google.gson.annotations.SerializedName

data class ProductListDTO(
    val id: Int,
    val title: String,
    val description: String?,
    val price: Int,
    val category: String?,
    val address: String?,
    val status: String,

    @SerializedName("main_image_url")
    val mainImageUrl: String?
)
