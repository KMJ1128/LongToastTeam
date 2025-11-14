package com.longtoast.bilbil.api

import com.longtoast.bilbil.ProductListResponse
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest // 🚨 추가
import com.longtoast.bilbil.dto.MemberTokenResponse // 💡 MemberTokenResponse import 추가
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 서버와의 통신을 위한 API 인터페이스
 * Retrofit2에서 사용됩니다.
 */
interface ApiService {

    // 서버의 POST /kakao/login/token 엔드포인트와 매칭됩니다.
    // 요청 본문으로 KakaoTokenRequest를 보내고, 응답으로 MsgEntity를 받습니다.
    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    /**
     * 🚨 새 게시글 작성 API: POST /writeproduct/create
     * 요청 본문으로 ProductCreateRequest를 보내고, 응답으로 MsgEntity를 받습니다.
     */
    @POST("writeproduct/create") // 🚨 수정: 'value =' 를 제거하고 경로만 넣거나
    // 또는 @POST("/writeproduct/create") 로 수정
    fun createProduct(
        @Body request: ProductCreateRequest
    ): Call<MsgEntity>

    @POST("/location/update")
    suspend fun sendLocation(@Body request: LocationRequest): retrofit2.Response<Void>


    // 다른 API 엔드포인트가 필요하면 여기에 추가합니다.
    // @GET("/member/info")
    // fun getMemberInfo(@Header("Authorization") token: String): Call<MsgEntity>



    @GET("/products/lists")
    suspend fun getProductLists(
        // title: 사용자가 입력한 검색어
        @Query("title") title: String?,
        // category: 카테고리 필터링
        @Query("category") category: String?,
        // sort: 정렬 기준 (예: "latest", "price_asc")
        @Query("sort") sort: String?
    ): retrofit2.Response<ProductListResponse.ProductListResponse> // Coroutine과 함께 사용하기 위해 Response<T>를 반환합니다.
}