package com.longtoast.bilbil.api

import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.longtoast.bilbil.dto.ReviewCreateRequest
import com.longtoast.bilbil.dto.ProductCreateRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    @POST("/naver/login/token")
    fun loginWithNaverToken(@Body request: NaverTokenRequest): Call<MsgEntity>

    @GET("/products/myitems")
    fun getMyRegisteredProducts(): Call<MsgEntity>

    @GET("/products/myrentals")
    fun getMyRentedProducts(): Call<MsgEntity>

    @GET("/products/lists")
    fun getProductLists(
        @Query("title") title: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null
    ): Call<MsgEntity>

    @Multipart
    @POST("writeproduct/create")
    fun createProduct(
        @Part("product") productJson: RequestBody,
        @Part images: List<MultipartBody.Part>
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

    @Multipart
    @POST("/api/chat/room/{roomId}/image")
    fun uploadChatImage(
        @Path("roomId") roomId: String,
        @Part image: MultipartBody.Part
    ): Call<MsgEntity>

    // 🔥 프로필 업데이트 API (단 하나만)
    @Multipart
    @PUT("/member/profile")
    fun updateProfile(
        @Part("member") memberJson: RequestBody,
        @Part profileImage: MultipartBody.Part?
    ): Call<MsgEntity>

    @GET("/search/popular")
    fun getPopularSearches(): Call<MsgEntity>

    @GET("/search/history")
    fun getMySearchHistory(): Call<MsgEntity>
}
