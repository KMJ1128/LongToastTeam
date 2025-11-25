package com.longtoast.bilbil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.longtoast.bilbil.dto.ChatRoomListDTO
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ChatNotificationHelper {

    private const val PREFS_NAME = "ChatNotificationPrefs"
    private const val KEY_LAST_SNAPSHOT = "lastSnapshot"
    private const val CHANNEL_ID = "chat_updates"

    private val gson = Gson()
    private val snapshotType = object : TypeToken<Map<String, String>>() {}.type

    fun detectNewMessages(context: Context, rooms: List<ChatRoomListDTO>): List<ChatRoomListDTO> {
        val prefs = getPrefs(context)
        val cached = prefs.getString(KEY_LAST_SNAPSHOT, null)
        if (cached.isNullOrBlank()) {
            saveSnapshot(prefs, rooms)
            return emptyList()
        }

        val previous: Map<String, String> = try {
            gson.fromJson(cached, snapshotType) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        val newRooms = rooms.filter { room ->
            val roomId = room.roomId?.toString() ?: return@filter false
            val lastTime = room.lastMessageTime
            val previousTime = previous[roomId]

            // 새로 등장한 방이거나, 마지막 메시지 시각이 변경된 경우 신규 메시지로 판단
            previousTime == null || (lastTime != null && previousTime != lastTime)
        }

        saveSnapshot(prefs, rooms)
        return newRooms
    }

    fun saveSnapshot(context: Context, rooms: List<ChatRoomListDTO>) {
        val prefs = getPrefs(context)
        saveSnapshot(prefs, rooms)
    }

    private fun saveSnapshot(prefs: SharedPreferences, rooms: List<ChatRoomListDTO>) {
        val snapshot = rooms
            .mapNotNull { room -> room.roomId?.toString()?.let { it to (room.lastMessageTime ?: "") } }
            .toMap()
        prefs.edit().putString(KEY_LAST_SNAPSHOT, gson.toJson(snapshot)).apply()
    }

    fun showNewMessageNotification(context: Context, room: ChatRoomListDTO) {
        if (!canPostNotifications(context)) return

        ensureChannel(context)

        val intent = Intent(context, ChatRoomActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ROOM_ID", room.roomId)
            putExtra("SELLER_NICKNAME", room.partnerNickname)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            room.roomId ?: System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentTitle = room.partnerNickname ?: context.getString(R.string.app_name)
        val contentText = room.lastMessageContent ?: context.getString(R.string.app_name)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(room.roomId ?: 0, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) return false
        }

        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
