package com.longtoast.bilbil

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.longtoast.bilbil.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 백그라운드에서 주기적으로 채팅 목록을 조회하여 신규 메시지를 알림으로 띄워준다.
 */
class ChatRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
//
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = AuthTokenManager.getToken()
        if (token.isNullOrBlank()) {
            Log.d("ChatRefreshWorker", "토큰이 없어 작업을 건너뜀")
            return@withContext Result.success()
        }

        return@withContext runCatching {
            val response = RetrofitClient.getApiService().getMyChatRooms().execute()
            if (!response.isSuccessful) {
                Log.w(
                    "ChatRefreshWorker",
                    "채팅 목록 조회 실패: ${response.code()} ${response.message()}"
                )
                return@runCatching Result.retry()
            }

            val rooms = ChatRoomListParser.parseFromMsgEntity(response.body())
            if (rooms.isEmpty()) {
                ChatNotificationHelper.saveSnapshot(applicationContext, emptyList())
                return@runCatching Result.success()
            }

            val newMessages = ChatNotificationHelper.detectNewMessages(applicationContext, rooms)
            if (newMessages.isEmpty()) {
                Log.d("ChatRefreshWorker", "신규 메시지 없음. 스냅샷만 갱신")
                return@runCatching Result.success()
            }

            newMessages.forEach { room ->
                ChatNotificationHelper.showNewMessageNotification(applicationContext, room)
            }

            Result.success()
        }.getOrElse { error ->
            Log.e("ChatRefreshWorker", "백그라운드 새로고침 실패", error)
            Result.retry()
        }
    }
}
