package com.balatromp.nativebridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.balatromp.nativebridge.MainActivity
import com.balatromp.nativebridge.R
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.*

class MultiplayerService : LifecycleService() {

    private var server: ApplicationEngine? = null
    private val rooms = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.server_starting)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (server == null) startServer()
        return START_STICKY
    }

    private fun startServer() {
        server = embeddedServer(CIO, port = SERVER_PORT) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    handleConnection(this)
                }
            }
        }.start(wait = false)
        updateNotification(getString(R.string.server_running_port, SERVER_PORT))
    }

    // Basic protocol: JOIN:[ROOM_ID] or MSG:[CONTENT]
    private suspend fun handleConnection(session: DefaultWebSocketServerSession) {
        var currentRoom: String? = null
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                when {
                    text.startsWith(PREFIX_JOIN) -> {
                        val roomId = text.substringAfter(PREFIX_JOIN)
                        currentRoom = roomId
                        rooms.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(session)
                    }
                    text.startsWith(PREFIX_MSG) -> relayToRoom(currentRoom, text.substringAfter(PREFIX_MSG), session)
                }
            }
        } finally {
            leaveRoom(currentRoom, session)
        }
    }

    private suspend fun relayToRoom(roomId: String?, message: String, sender: DefaultWebSocketServerSession) {
        if (roomId == null) return
        rooms[roomId]?.forEach { other ->
            if (other != sender) other.send(PREFIX_MSG + message)
        }
    }

    private fun leaveRoom(roomId: String?, session: DefaultWebSocketServerSession) {
        if (roomId == null) return
        val room = rooms[roomId] ?: return
        room.remove(session)
        if (room.isEmpty()) rooms.remove(roomId, room)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Placeholder icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        super.onDestroy()
    }

    companion object {
        const val SERVER_PORT = 8788
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MultiplayerServiceChannel"
        private const val PREFIX_JOIN = "JOIN:"
        private const val PREFIX_MSG = "MSG:"
    }
}
