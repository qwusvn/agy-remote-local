package com.example.agyremote.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed class AgyServerEvent {
    data class Connected(val url: String) : AgyServerEvent()
    data class Disconnected(val reason: String) : AgyServerEvent()
    data class AgentCompleted(val convoId: String, val title: String, val summary: String, val url: String = "") : AgyServerEvent()
    data class UserActionRequired(val question: String) : AgyServerEvent()
    data class NotificationAlert(val title: String, val body: String) : AgyServerEvent()
    data class SessionWorking(val convoId: String, val title: String) : AgyServerEvent()
}

class AgyWebSocketClient(
    private val hostIp: String,
    private val port: Int = 4400,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    private val _events = MutableSharedFlow<AgyServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgyServerEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Không timeout cho WebSocket
        .build()

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            connect()
        }
    }

    fun stop() {
        isRunning.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "App closed")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webSocket = null
    }

    private fun connect() {
        if (!isRunning.get()) return

        val wsUrl = "ws://$hostIp:$port/connect-websocket"
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch {
                    _events.emit(AgyServerEvent.Connected(wsUrl))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch {
                    _events.emit(AgyServerEvent.Disconnected("Closed: $reason"))
                }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    _events.emit(AgyServerEvent.Disconnected("Error: ${t.message}"))
                }
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!isRunning.get()) return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch(Dispatchers.IO) {
            var delayMs = 2000L
            while (isRunning.get() && isActive) {
                delay(delayMs)
                try {
                    connect()
                    break
                } catch (e: Exception) {
                    delayMs = (delayMs * 2).coerceAtMost(20000L)
                }
            }
        }
    }

    private fun handleIncomingMessage(rawText: String) {
        scope.launch {
            try {
                val json = try { JSONObject(rawText) } catch (e: Exception) { null }
                val type = json?.optString("type") ?: ""

                if (type == "AGENT_COMPLETED") {
                    val convoId = json?.optString("convoId") ?: ""
                    val rawTitle = json?.optString("title") ?: "Phiên làm việc"
                    var cleanTitle = rawTitle.replace("<USER_REQUEST>", "").replace("</USER_REQUEST>", "").trim()
                    if (cleanTitle.contains("\n")) {
                        cleanTitle = cleanTitle.split("\n").firstOrNull { it.isNotBlank() }?.trim() ?: cleanTitle
                    }
                    if (cleanTitle.length > 50) {
                        cleanTitle = cleanTitle.take(47) + "..."
                    }
                    val rawSummary = json?.optString("summary") ?: "Agent đã hoàn tất câu trả lời"
                    var summary = rawSummary
                        .replace("<USER_REQUEST>", "")
                        .replace("</USER_REQUEST>", "")
                        .replace(Regex("<[^>]*>"), "")
                        .trim()
                    if (summary.isBlank() || summary.contains("Created At:") || summary.contains("The command exited")) {
                        summary = "Agent đã hoàn tất câu trả lời"
                    }
                    val rawUrl = json?.optString("url") ?: ""
                    val url = if (rawUrl.contains("localhost") || rawUrl.contains("127.0.0.1")) {
                        rawUrl.replace("localhost", hostIp).replace("127.0.0.1", hostIp)
                    } else if (rawUrl.isBlank() && convoId.isNotBlank()) {
                        "http://$hostIp:$port/c/$convoId"
                    } else {
                        rawUrl
                    }
                    _events.emit(AgyServerEvent.AgentCompleted(convoId, cleanTitle.ifBlank { "Phiên làm việc" }, summary, url))
                } else if (type == "SESSION_START" || type == "SESSION_WORKING") {
                    val convoId = json?.optString("convoId") ?: ""
                    val rawTitle = json?.optString("title") ?: "Phiên làm việc"
                    val cleanTitle = rawTitle.replace("<USER_REQUEST>", "").replace("</USER_REQUEST>", "").trim()
                    _events.emit(AgyServerEvent.SessionWorking(convoId, cleanTitle.ifBlank { "Phiên làm việc" }))
                } else if (rawText.contains("ASK_QUESTION") || rawText.contains("ask_question")) {
                    _events.emit(
                        AgyServerEvent.UserActionRequired("Antigravity đang chờ bạn trả lời hoặc chọn phương án")
                    )
                } else if (rawText.contains("request_review") || rawText.contains("AutoRunDecision")) {
                    _events.emit(
                        AgyServerEvent.UserActionRequired("Antigravity yêu cầu xác nhận chạy lệnh hệ thống")
                    )
                } else if (type == "notification") {
                    val title = json?.optString("title") ?: ""
                    val body = json?.optString("body") ?: ""
                    if (title.isNotBlank() && body.isNotBlank()) {
                        _events.emit(AgyServerEvent.NotificationAlert(title, body))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
