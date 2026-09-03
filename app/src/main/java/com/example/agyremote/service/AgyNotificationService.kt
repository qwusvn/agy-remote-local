package com.example.agyremote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.agyremote.MainActivity
import com.example.agyremote.R
import com.example.agyremote.network.AgyServerEvent
import com.example.agyremote.network.AgyWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AgyNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsClient: AgyWebSocketClient? = null

    companion object {
        const val CHANNEL_FOREGROUND_ID = "agy_foreground_channel"
        const val CHANNEL_ALERTS_ID = "agy_alerts_channel"

        const val NOTIFICATION_FOREGROUND_ID = 1001
        const val NOTIFICATION_ALERT_ID = 1002

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_HOST_IP = "EXTRA_HOST_IP"
        const val EXTRA_PORT = "EXTRA_PORT"

        fun start(context: Context, hostIp: String, port: Int = 4400) {
            val intent = Intent(context, AgyNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HOST_IP, hostIp)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgyNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
        fun ensureChannelsCreated(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                // 1. Kênh duy trì dịch vụ chạy ngầm
                val fgChannel = NotificationChannel(
                    CHANNEL_FOREGROUND_ID,
                    "Trạng thái kết nối AGY",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Duy trì kết nối thời gian thực với Antigravity trên máy tính"
                }

                // 2. Kênh thông báo nổi (Heads-up Notification)
                val alertChannel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    "Cảnh báo & Tác vụ AGY",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi Agent hoàn tất tác vụ hoặc cần bạn phê duyệt lệnh"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

                manager.createNotificationChannel(fgChannel)
                manager.createNotificationChannel(alertChannel)
            }
        }

        fun showPushNotification(context: Context, title: String, message: String, targetUrl: String? = null) {
            try {
                ensureChannelsCreated(context)

                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .setAutoCancel(true)
                    .setContentIntent(createOpenAppPendingIntent(context, targetUrl))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                val id = (System.currentTimeMillis() % 10000).toInt() + 2000
                manager.notify(id, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun showSessionNotification(context: Context, convoId: String, title: String, message: String, targetUrl: String?) {
            try {
                ensureChannelsCreated(context)

                val notifTitle = if (title.isNotBlank() && title != "Phiên Antigravity") "💬 $title" else "💬 Cuộc trò chuyện Antigravity"
                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle(notifTitle)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 250, 150, 250))
                    .setAutoCancel(true)
                    .setContentIntent(createOpenAppPendingIntent(context, targetUrl))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                val id = (convoId.hashCode() and 0x7FFFFFFF) % 10000 + 3000
                manager.notify(id, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun createOpenAppPendingIntent(context: Context, targetUrl: String? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!targetUrl.isNullOrBlank()) {
                    putExtra("EXTRA_TARGET_URL", targetUrl)
                }
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getActivity(context, targetUrl.hashCode() and 0xFFFF, intent, flags)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                wsClient?.stop()
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val hostIp = intent.getStringExtra(EXTRA_HOST_IP) ?: "192.168.1.220"
                val port = intent.getIntExtra(EXTRA_PORT, 4400)

                try {
                    val notification = createForegroundNotification(hostIp, port)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_FOREGROUND_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_FOREGROUND_ID, notification)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                setupWebSocket(hostIp, port)
            }
        }
        return START_STICKY
    }

    private fun setupWebSocket(hostIp: String, port: Int) {
        wsClient?.stop()
        val client = AgyWebSocketClient(hostIp, port, serviceScope)
        wsClient = client

        client.events.onEach { event ->
            when (event) {
                is AgyServerEvent.AgentCompleted -> {
                    showSessionNotification(this, event.convoId, event.title, event.summary, event.url.ifBlank { "http://$hostIp:$port/c/${event.convoId}" })

                    // Phát broadcast cho UI MainScreen nếu đang mở để cập nhật tức thời
                    val broadcastIntent = Intent("com.example.agyremote.SESSION_UPDATE").apply {
                        putExtra("convoId", event.convoId)
                        putExtra("title", event.title)
                        putExtra("isWorking", false)
                    }
                    sendBroadcast(broadcastIntent)
                }
                is AgyServerEvent.SessionWorking -> {
                    val broadcastIntent = Intent("com.example.agyremote.SESSION_UPDATE").apply {
                        putExtra("convoId", event.convoId)
                        putExtra("title", event.title)
                        putExtra("isWorking", true)
                    }
                    sendBroadcast(broadcastIntent)
                }
                is AgyServerEvent.UserActionRequired -> {
                    showPushNotification(this, "⚠️ Cần bạn xác nhận", event.question)
                }
                is AgyServerEvent.NotificationAlert -> {
                    showPushNotification(this, event.title, event.body)
                }
                is AgyServerEvent.Connected -> {
                    updateForegroundNotification("🟢 Đang kết nối LAN: $hostIp:$port")
                }
                is AgyServerEvent.Disconnected -> {
                    updateForegroundNotification("AGY Remote: Sẵn sàng kết nối")
                }
            }
        }.launchIn(serviceScope)

        client.start()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Kênh duy trì dịch vụ chạy ngầm
            val fgChannel = NotificationChannel(
                CHANNEL_FOREGROUND_ID,
                "Trạng thái kết nối AGY",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối thời gian thực với Antigravity trên máy tính"
            }

            // Kênh thông báo nổi khi có sự kiện quan trọng (Heads-up notification)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Cảnh báo & Tác vụ AGY",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi Agent trả lời xong hoặc cần bạn phê duyệt lệnh"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(fgChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(hostIp: String, port: Int) =
        NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setContentTitle("AGY Remote")
            .setContentText("🟢 Sẵn sàng phục vụ • LAN: $hostIp:$port")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent(this))
            .build()

    private fun updateForegroundNotification(status: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setContentTitle("AGY Remote")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent(this))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_FOREGROUND_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient?.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
