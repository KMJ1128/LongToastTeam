// com.longtoast.bilbil.api.ApiService.kt
package com.longtoast.bilbil.api

import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.longtoast.bilbil.dto.MemberDTO // 💡 MemberDTO Import
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT // 💡 PUT 메서드 Import
import retrofit2.http.Path

interface ApiService {

    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    /**
     * 네이버 토큰으로 로그인하는 API 정의
     */
    @POST("/naver/login/token")
    fun loginWithNaverToken(@Body request: NaverTokenRequest): Call<MsgEntity>

    /**
     * ✅ [핵심 수정] 상품 생성 API.
     * NewPostFragment에서 호출하는 메서드 정의가 명확하게 포함되어야 합니다.
     */
    @GET("products/myitems")
    fun getMyProducts(): Call<MsgEntity>

    @POST("writeproduct/create")
    fun createProduct(
        @Body request: ProductCreateRequest
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
     * ✅ [핵심 추가] 프로필 업데이트 API (MemberController의 PUT /member/profile과 일치)
     */
    @PUT("member/profile")
    fun updateProfile(@Body memberDTO: MemberDTO): Call<MsgEntity> // 💡 @Body 파라미터와 반환 타입 일치
}