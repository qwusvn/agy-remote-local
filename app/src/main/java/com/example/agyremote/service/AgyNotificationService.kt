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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                wsClient?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val hostIp = intent.getStringExtra(EXTRA_HOST_IP) ?: "192.168.1.220"
                val port = intent.getIntExtra(EXTRA_PORT, 4400)

                startForeground(
                    NOTIFICATION_FOREGROUND_ID,
                    createForegroundNotification(hostIp, port)
                )

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
                    showAlertNotification("Hoàn thành tác vụ", event.summary)
                }
                is AgyServerEvent.UserActionRequired -> {
                    showAlertNotification("Cần bạn xác nhận", event.question)
                }
                is AgyServerEvent.NotificationAlert -> {
                    showAlertNotification(event.title, event.body)
                }
                is AgyServerEvent.Connected -> {
                    updateForegroundNotification("Đã kết nối: $hostIp:$port")
                }
                is AgyServerEvent.Disconnected -> {
                    updateForegroundNotification("Mất kết nối: Đang thử lại...")
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

            // Kênh thông báo nổi khi có sự kiện quan trọng
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Cảnh báo & Tác vụ AGY",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi Agent trả lời xong hoặc cần bạn phê duyệt lệnh"
                enableVibration(true)
            }

            manager.createNotificationChannel(fgChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createForegroundNotification(hostIp: String, port: Int) =
        NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setContentTitle("AGY Remote đang chạy ngầm")
            .setContentText("Kết nối: $hostIp:$port")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent())
            .build()

    private fun updateForegroundNotification(status: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_FOREGROUND_ID)
            .setContentTitle("AGY Remote")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent())
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_FOREGROUND_ID, notification)
    }

    private fun showAlertNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent())
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ALERT_ID, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient?.stop()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
