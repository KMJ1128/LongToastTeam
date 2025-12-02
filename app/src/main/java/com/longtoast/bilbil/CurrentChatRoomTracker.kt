package com.longtoast.bilbil

import android.content.Context

/**
 * 현재 열려 있는 채팅방의 roomId 를 저장/조회하는 헬퍼.
 * - ChatRoomActivity 에서 onResume/onPause 때 setCurrentRoom() 호출
 * - FCM, WorkManager 등에서는 getCurrentRoom() 으로 읽어서
 *   같은 방이면 알림을 띄우지 않도록 한다.
 */
object CurrentChatRoomTracker {

    private const val PREFS_NAME = "ActiveChatRoomPrefs"
    private const val KEY_CURRENT_ROOM_ID = "currentRoomId"

    /** 현재 보고 있는 채팅방 ID 기록 (null 이면 삭제) */
    fun setCurrentRoom(context: Context, roomId: Int?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (roomId != null && roomId > 0) {
                putInt(KEY_CURRENT_ROOM_ID, roomId)
            } else {
                remove(KEY_CURRENT_ROOM_ID)
            }
        }.apply()
    }

    /** 현재 보고 있는 채팅방 ID 조회 (없으면 null) */
    fun getCurrentRoom(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_CURRENT_ROOM_ID, -1)
        return if (stored > 0) stored else null
    }
}
