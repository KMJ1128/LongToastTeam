package com.longtoast.bilbil

import com.google.gson.annotations.SerializedName

data class Product(
    val id: Int,
    val title: String,
    val price: Int?,
    val description: String?,
    val category: String?,
    val deposit: Int? = null,
    val status: String? = null,
    @SerializedName("imageUrls")
    val imageUrls: List<String>? = emptyList()
)
