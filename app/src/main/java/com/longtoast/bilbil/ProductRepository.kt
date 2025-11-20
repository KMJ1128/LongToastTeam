package com.longtoast.bilbil

import retrofit2.HttpException
import java.io.IOException

class ProductRepository(private val api: ProductService) {

    /**
     * @param searchQuery : title 파라미터로 전달
     * @param category : category 파라미터로 전달
     */
    suspend fun getProductList(searchQuery: String? = null, category: String? = null, sort: String? = null): Result<List<Product>> {
        return try {
            val resp = api.getProductLists(title = searchQuery, category = category, sort = sort)
            if (resp.isSuccessful) {
                val body = resp.body()
                val list = body?.data ?: emptyList()
                Result.success(list)
            } else {
                // 서버 에러 본문을 뽑아서 전달
                val err = resp.errorBody()?.string()
                Result.failure(Exception("HTTP ${resp.code()}: $err"))
            }
        } catch (e: IOException) {
            // 네트워크 오류 (timeout 등)
            Result.failure(e)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSellerProducts(userId: Int): Result<List<Product>> {
        return try {
            val resp = api.getProductsBySellerId(userId) // 💡 새로 추가한 API 호출

            if (resp.isSuccessful) {
                val body = resp.body()
                val list = body?.data ?: emptyList()
                Result.success(list)
            } else {
                val err = resp.errorBody()?.string()
                Result.failure(Exception("HTTP ${resp.code()}: $err"))
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
