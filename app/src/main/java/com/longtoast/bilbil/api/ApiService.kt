// com.longtoast.bilbil.api.ApiService.kt
package com.longtoast.bilbil.api

import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.longtoast.bilbil.dto.MemberDTO // 💡 MemberDTO Import
import com.longtoast.bilbil.dto.ReviewCreateRequest

import com.longtoast.bilbil.dto.ProductCreateRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Multipart
import retrofit2.http.Part

interface ApiService {

    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    @POST("/naver/login/token")
    fun loginWithNaverToken(@Body request: NaverTokenRequest): Call<MsgEntity>

    @GET("products/myitems")
    fun getMyProducts(): Call<MsgEntity>

    @GET("/products/lists")
    fun getProductLists(
        @Query("title") title: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null
    ): Call<MsgEntity>

    /**
     * ✅ [핵심 수정] 상품 생성 API - JSON 요청으로 복구
     * 서버 WriteProductController에 맞춰 @RequestBody ProductDTO를 보냅니다.
     */
    @POST("writeproduct/create")
    fun createProduct(
        @Body request: ProductCreateRequest
    ): Call<MsgEntity>

    @GET("/products/{itemId}")
    suspend fun getProductDetail(
        @Path("itemId") itemId: Int
    ): Response<MsgEntity>

    @POST("/reviews")
    fun createReview(
        @Body request: ReviewCreateRequest
    ): Call<MsgEntity>


    @POST("/location/update")
    suspend fun sendLocation(@Body request: LocationRequest): Response<MsgEntity>


    @POST("/api/chat/room")
    fun createChatRoom(
        @Body request: ChatRoomCreateRequest
    ): Call<MsgEntity>

    @GET("/chat/rooms")
    fun getMyChatRooms(): Call<MsgEntity>

    @GET("/api/chat/history/{roomId}")
    fun getChatHistory(@Path("roomId") roomId: String): Call<MsgEntity>

    /**
    ✅ [핵심 추가] 프로필 업데이트 API (MemberController의 PUT /member/profile과 일치)*/@PUT("member/profile")
    fun updateProfile(@Body memberDTO: MemberDTO): Call<MsgEntity> // 💡 @Body 파라미터와 반환 타입 일치

    @GET("/search/popular")
    fun getPopularSearches(): Call<MsgEntity>

    @PUT("member/profile")
    fun updateProfile(@Body memberDTO: MemberDTO): Call<MsgEntity>
}