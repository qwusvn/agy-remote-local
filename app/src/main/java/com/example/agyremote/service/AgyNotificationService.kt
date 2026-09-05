package com.example.agyremote.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
                    setShowBadge(false)
                }

                // 2. Kênh thông báo nổi (Heads-up Notification)
                val alertSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val alertChannel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    "Cảnh báo & Tác vụ AGY",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi Agent hoàn tất tác vụ hoặc cần bạn phê duyệt lệnh"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setSound(alertSoundUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }

                manager.createNotificationChannel(fgChannel)
                manager.createNotificationChannel(alertChannel)
            }
        }

        fun playHapticAndAudio(context: Context) {
            try {
                // Kích hoạt rung máy
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                }

                // Phát âm thanh thông báo
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, soundUri)
                ringtone?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Volatile
        var isAppInForeground: Boolean = false

        fun clearAlertNotification(context: Context) {
            try {
                val manager = NotificationManagerCompat.from(context)
                manager.cancel(NOTIFICATION_ALERT_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private var lastNotifiedTime: Long = 0L
        private var lastNotifiedMessage: String = ""

        fun showPushNotification(context: Context, title: String, message: String, targetUrl: String? = null) {
            // Nếu người dùng đang mở app và nhìn trực tiếp màn hình thì không bắn thông báo làm phiền
            if (isAppInForeground) {
                return
            }

            val now = System.currentTimeMillis()
            // Lọc triệt để theo yêu cầu: Chỉ hiện thông báo kết luận cuối cùng, bỏ qua toàn bộ log lệnh/tool/Created At
            if (message.isBlank() || 
                message.contains("Created At:") || 
                message.contains("Completed At:") || 
                message.contains("The command exited") ||
                message.contains("task-") ||
                message.contains("<USER_REQUEST>") ||
                message.startsWith("Step ") ||
                message.startsWith("Ran ") ||
                message.startsWith("Edited ")
            ) {
                return
            }
            if (now - lastNotifiedTime < 6000L && (message == lastNotifiedMessage || now - lastNotifiedTime < 3000L)) {
                return
            }
            lastNotifiedTime = now
            lastNotifiedMessage = message

            try {
                ensureChannelsCreated(context)
                playHapticAndAudio(context)

                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(longArrayOf(0, 300, 200, 300))
                    .setAutoCancel(true)
                    .setContentIntent(createOpenAppPendingIntent(context, targetUrl))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                // Luôn dùng 1 ID duy nhất để ghi đè thông báo cũ, không bao giờ gom nhóm hoặc tích tụ rác
                manager.notify(NOTIFICATION_ALERT_ID, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun showSessionNotification(context: Context, convoId: String, title: String, message: String, targetUrl: String?) {
            // Nếu người dùng đang mở app thì không bắn thông báo nổi để tránh rác màn hình
            if (isAppInForeground) {
                return
            }

            val now = System.currentTimeMillis()
            // Lọc triệt để: Chỉ hiện thông báo kết luận cuối cùng, bỏ qua toàn bộ log lệnh/tool/Created At
            if (message.isBlank() || 
                message.contains("Created At:") || 
                message.contains("Completed At:") || 
                message.contains("The command exited") || 
                message.contains("task-") ||
                message.contains("<USER_REQUEST>") ||
                message.startsWith("Step ") ||
                message.startsWith("Ran ") ||
                message.startsWith("Edited ")
            ) {
                return
            }
            if (now - lastNotifiedTime < 6000L && (message == lastNotifiedMessage || now - lastNotifiedTime < 3000L)) {
                return
            }
            lastNotifiedTime = now
            lastNotifiedMessage = message

            try {
                ensureChannelsCreated(context)
                playHapticAndAudio(context)

                val notifTitle = if (title.isNotBlank() && title != "Phiên Antigravity") "💬 $title" else "💬 Cuộc trò chuyện Antigravity"
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setContentTitle(notifTitle)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(longArrayOf(0, 250, 150, 250))
                    .setAutoCancel(true)
                    .setContentIntent(createOpenAppPendingIntent(context, targetUrl))
                    .build()

                val manager = NotificationManagerCompat.from(context)
                // Dùng chung NOTIFICATION_ALERT_ID để ghi đè thay thế, không spam nhiều thông báo riêng lẻ
                manager.notify(NOTIFICATION_ALERT_ID, notification)
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
                    val isToolLog = event.body.contains("Created At:") ||
                                    event.body.contains("Completed At:") ||
                                    event.body.contains("The command exited") ||
                                    event.body.contains("task-") ||
                                    event.body.startsWith("Step ") ||
                                    event.body.startsWith("Ran ")
                    if (!isToolLog && event.body.isNotBlank()) {
                        showPushNotification(this, event.title, event.body)
                    }
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
