package com.longtoast.bilbil.api

import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.ReviewCreateRequest
import com.longtoast.bilbil.dto.ProductCreateRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @POST("writeproduct/create")
    fun createProduct(
        @Body request: ProductCreateRequest
    ): Call<MsgEntity>

    // 🔥 [수정됨] 백엔드: @GetMapping("/{itemId}") -> /products/{itemId} 라고 가정
    // 만약 Controller 위에 @RequestMapping("/products")가 있다면 아래가 맞습니다.
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

    // 🔥 프로필 업데이트 API (단 하나만)
    @PUT("/member/profile")
    fun updateProfile(@Body memberDTO: MemberDTO): Call<MsgEntity>

    @GET("/search/popular")
    fun getPopularSearches(): Call<MsgEntity>
}
