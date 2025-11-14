package com.longtoast.bilbil

import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * TokenManager: 앱에서 로그인 후 토큰을 여기 저장하면 자동으로 Authorization 헤더가 추가됩니다.
 * (디버깅용으로 기본값을 넣고 싶으면 아래 변수에 값 할당하세요.)
 */
object TokenManager {
    // 예시(디버그): TokenManager.token = "eyJhbGciOiJIUzI1NiJ9..."
    var token: String? = null

    fun getCurrentUserId(): Int? {
        // 실제 구현에서는 JWT 토큰에서 'sub'(subject) 클레임을 파싱하거나,
        // 로그인 성공 시 저장한 사용자 ID를 반환해야 합니다.
        // 테스트를 위해 임시로 1번 사용자의 ID를 반환합니다.
        return 1
    }
}

private const val BASE_URL = "http://172.16.102.73:8080/" // 실제 환경에 맞게 변경하세ddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd

// Authorization 인터셉터: 토큰이 있으면 "Bearer <token>" 헤더 자동 추가
private val authInterceptor = Interceptor { chain ->
    val original: Request = chain.request()
    val builder: Request.Builder = original.newBuilder()
        .header("Accept", "application/json")
    TokenManager.token?.let { token ->
        builder.header("Authorization", "Bearer $token")
    }
    chain.proceed(builder.build())
}

// 로깅 인터셉터 (디버그용)
private val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}

private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .addInterceptor(loggingInterceptor)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

private val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(httpClient)
    .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
    .build()

object ApiClient {
    val productService: ProductService = retrofit.create(ProductService::class.java)
    val userService: UserService = retrofit.create(UserService::class.java)
    //val chatService: ChatService = retrofit.create(ChatService::class.java)
}

interface ProductService {
    /**
     * GET /products/lists
     * 서버가 반환하는 형태가 {"message":"...", "data":[{...}, ...]} 이므로
     * Response<MsgEntity<List<Product>>> 형태로 받습니다.
     */
    @GET("products/lists")
    suspend fun getProductLists(
        @Query("title") title: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null
    ): Response<MsgEntity<List<Product>>>

    @GET("products/seller/{userId}")
    suspend fun getProductsBySellerId(
        // 💡 @Path: URL 경로 변수 {userId}에 사용자 ID를 매핑
        @Path("userId") userId: Int
    ): Response<MsgEntity<List<Product>>>
}

interface UserService {
    @GET("member/info")
    suspend fun getMyInfo(): Response<MsgEntity<MemberTokenResponse>>
}

//interface ChatService {
//    @GET("chat/rooms")
//    suspend fun getChatRooms(): Response<MsgEntity<List<ChatRoom>>>
//}
