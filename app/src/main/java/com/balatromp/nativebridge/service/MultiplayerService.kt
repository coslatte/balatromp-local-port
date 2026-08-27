package com.balatromp.nativebridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.balatromp.nativebridge.MainActivity
import com.balatromp.nativebridge.R
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.*

class MultiplayerService : LifecycleService() {

    private var server: ApplicationEngine? = null
    private val CHANNEL_ID = "MultiplayerServiceChannel"

    // Matchmaking logic state
    private val rooms = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification("Server is starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (server == null) {
            startServer()
        }
        
        return START_STICKY
    }

    private fun startServer() {
        server = embeddedServer(CIO, port = 8788) {
            install(WebSockets)
            routing {
                webSocket("/") {
                    handleConnection(this)
                }
            }
        }.start(wait = false)
        
        updateNotification("Server is running on port 8788")
    }

    private suspend fun handleConnection(session: DefaultWebSocketServerSession) {
        var currentRoom: String? = null
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    // Basic protocol: JOIN:[ROOM_ID] or MSG:[CONTENT]
                    when {
                        text.startsWith("JOIN:") -> {
                            val roomId = text.substringAfter("JOIN:")
                            currentRoom = roomId
                            rooms.getOrPut(roomId) { ConcurrentHashMap.newKeySet() }.add(session)
                        }
                        text.startsWith("MSG:") -> {
                            val msg = text.substringAfter("MSG:")
                            currentRoom?.let { roomId ->
                                rooms[roomId]?.forEach { otherSession ->
                                    if (otherSession != session) {
                                        otherSession.send("MSG:$msg")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            currentRoom?.let { rooms[it]?.remove(session) }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Multiplayer Server Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Balatro Multiplayer Server")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Placeholder icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        super.onDestroy()
    }
}
