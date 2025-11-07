package com.longtoast.bilbil.api

import com.longtoast.bilbil.AuthTokenManager
import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ProductCreateRequest
import com.longtoast.bilbil.dto.MemberTokenResponse
import com.longtoast.bilbil.dto.ChatMsgEntity // 🚨 임포트 추가

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header

interface ApiService {

    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    @POST("writeproduct/create")
    fun createProduct(
        @Body request: ProductCreateRequest
    ): Call<MsgEntity>

    @POST("/location/update")
    suspend fun sendLocation(@Body request: LocationRequest): retrofit2.Response<Void>



    @POST("/api/chat/room")
    fun createChatRoom(@Body request: ChatRoomCreateRequest): Call<ChatMsgEntity> // 💡 ChatMsgEntity로 변경
}