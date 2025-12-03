package com.longtoast.bilbil.api

import com.longtoast.bilbil.dto.ChatRoomCreateRequest
import com.longtoast.bilbil.dto.KakaoTokenRequest
import com.longtoast.bilbil.dto.LocationRequest
import com.longtoast.bilbil.dto.MsgEntity
import com.longtoast.bilbil.dto.ChatMessage
import com.longtoast.bilbil.dto.ChatRoomReportRequest
import com.longtoast.bilbil.dto.ChatSendRequest
import com.longtoast.bilbil.dto.FcmTokenRequest
import com.longtoast.bilbil.dto.NaverTokenRequest
import com.longtoast.bilbil.dto.MemberDTO
import com.longtoast.bilbil.dto.ReviewCreateRequest
import com.longtoast.bilbil.dto.ProductCreateRequest
import com.longtoast.bilbil.dto.RentalApproveRequest
import com.longtoast.bilbil.dto.RentalDecisionRequest
import com.longtoast.bilbil.dto.RentalRequest
import com.longtoast.bilbil.dto.VerifyRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body

interface ApiService {

    @POST("/kakao/login/token")
    fun loginWithKakaoToken(@Body request: KakaoTokenRequest): Call<MsgEntity>

    @POST("/naver/login/token")
    fun loginWithNaverToken(@Body request: NaverTokenRequest): Call<MsgEntity>

    @GET("/member/info")
    fun getMyInfo(): Call<MsgEntity>

    @GET("/products/myitems")
    fun getMyRegisteredProducts(): Call<MsgEntity>

    @GET("/products/myrentals")
    fun getMyRentedProducts(): Call<MsgEntity>

    // 💡 수정됨: period 파라미터 추가
    @GET("/products/lists")
    fun getProductLists(
        @Query("title") title: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null,
        @Query("period") period: String? = null
    ): Call<MsgEntity>

    @Multipart
    @POST("/writeproduct/create")
    fun createProduct(
        @Part product: MultipartBody.Part,
        @Part images: List<MultipartBody.Part>
    ): Call<MsgEntity>

    @PUT("/writeproduct/update/{itemId}")
    fun updateProduct(
        @Path("itemId") itemId: Int,
        @Body request: ProductCreateRequest
    ): Call<MsgEntity>

    @DELETE("/writeproduct/delete/{itemId}")
    fun deleteProduct(@Path("itemId") itemId: Int): Call<MsgEntity>

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

    @GET("/reviews/seller/{sellerId}")
    fun getSellerReviews(
        @Path("sellerId") sellerId: Int
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
    fun getChatHistory(@Path("roomId") roomId: Int): Call<MsgEntity>

    @POST("/api/chat/room/{roomId}/message")
    fun sendChatMessage(
        @Path("roomId") roomId: Int,
        @Body request: ChatSendRequest
    ): Call<MsgEntity>

    @POST("/api/chat/room/{roomId}/message")
    fun sendChatMessage(
        @Path("roomId") roomId: String,
        @Body request: ChatSendRequest
    ): Call<MsgEntity>

    @Multipart
    @POST("/api/chat/room/{roomId}/image")
    fun uploadChatImage(
        @Path("roomId") roomId: Int,
        @Part image: MultipartBody.Part
    ): Call<MsgEntity>

    @POST("/chat/report")
    fun reportChatRoom(
        @Body request: ChatRoomReportRequest
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

    @POST("/rental/request")
    fun createRentalRequest(@Body request: RentalRequest): Call<MsgEntity>

    @POST("/rental/accept")
    fun acceptRental(@Body request: RentalDecisionRequest): Call<MsgEntity>

    @POST("/fcm/token")
    fun saveFcmToken(
        @Header("Authorization") auth: String,
        @Body request: FcmTokenRequest
    ): Call<ResponseBody>

    @POST("/api/rental/approve")
    fun approveRental(@Body request: RentalApproveRequest): Call<MsgEntity>

    @GET("/api/chat/room/{roomId}/info")
    fun getChatRoomInfo(@Path("roomId") roomId: Int): Call<MsgEntity>

    @GET("/api/rental/item/{itemId}/schedules")
    fun getRentalSchedules(
        @Path("itemId") itemId: Long
    ): Call<MsgEntity>

    // 🟢 [수정] 내가 쓴 리뷰 조회
    @GET("/reviews/my")
    fun getMyWrittenReviews(): Call<MsgEntity>

    // 🟢 [수정] 내가 받은 리뷰 조회
    @GET("/reviews/received")
    fun getMyReceivedReviews(): Call<MsgEntity>

    @GET("/reviews/lender-targets")
    fun getLenderReviewTargets(): Call<MsgEntity>



    @GET("/reviews/my/seller")
    fun getMyWrittenReviewsAsSeller(): Call<MsgEntity>

    @GET("/reviews/my/borrower")
    fun getMyWrittenReviewsAsBorrower(): Call<MsgEntity>

    // 🔥 추가: 역할별로 받은 리뷰
    @GET("/reviews/received/seller")
    fun getMyReceivedReviewsAsSeller(): Call<MsgEntity>

    @GET("/reviews/received/borrower")
    fun getMyReceivedReviewsAsBorrower(): Call<MsgEntity>


    // 1. 서버에 인증 요청 (서버가 코드를 생성하고 SMS URL을 반환)
    @POST("/member/verification/request")
    fun requestVerification(
        @Body request: VerifyRequest // VerifyRequest DTO 사용 (전화번호 필드 포함)
    ): Call<MsgEntity>

    // 2. 사용자가 문자를 보낸 후, 서버에 메일함 확인 및 인증 완료 요청
    @POST("/member/verification/confirm")
    fun confirmVerification(
        @Body request: VerifyRequest // VerifyRequest DTO 사용 (전화번호 필드 포함)
    ): Call<MsgEntity>

    @POST("/api/chat/room/{roomId}/read")
    fun markChatRead(
        @Path("roomId") roomId: Int
    ): Call<MsgEntity>
}