package com.longtoast.bilbil


import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ProductApiService {
    @GET("/products/lists")
    suspend fun getProductLists(
        @Query("title") title: String?,
        @Query("category") category: String?,
        @Query("sort") sort: String?
    ): Response<ProductListResponse>
}