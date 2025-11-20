package com.longtoast.bilbil

class ProductListResponse {
    data class ProductListResponse(
        val message: String,
        val data: List<Product>)
}