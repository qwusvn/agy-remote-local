package com.example.agyremote.data.cache

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Bộ đệm tài nguyên tĩnh (Static Asset Cache) cho WebView.
 * - Lưu cục bộ các file JS chunks (_next/static), CSS, Fonts, Icons, Images.
 * - Trả về tức thì từ bộ nhớ flash của máy (0-1ms), bỏ qua header no-store của máy chủ LAN.
 * - Giảm thiểu tối đa hiện tượng loading / giật lag khi chuyển đổi giao diện hoặc mở tab mới.
 */
class AgyResourceCache(context: Context) {

    private val cacheDir = File(context.cacheDir, "agy_web_cache").apply {
        if (!exists()) mkdirs()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        if (request.method.uppercase() != "GET") return null

        val uri = request.url ?: return null
        val url = uri.toString()
        val path = uri.path ?: ""

        // Bỏ qua các URL động, WebSocket, SSE Streams, OAuth callbacks và API
        if (url.startsWith("ws:") || url.startsWith("wss:")) return null
        if (url.contains("/stream") || url.contains("/agent_state") || url.contains("oauth-callback") || url.contains("code=")) return null
        if (path.startsWith("/api/")) return null

        // Chỉ cache các tài nguyên tĩnh: _next/static, JS chunks, CSS, fonts, ảnh
        val isNextStatic = path.contains("/_next/static/")
        val isStaticAsset = isNextStatic || isStaticExtension(path)

        if (!isStaticAsset) return null

        val hash = hashUrl(url)
        val cacheFile = File(cacheDir, "$hash.cache")
        val mimeType = getMimeType(path)

        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "public, max-age=31536000, immutable"
        )

        val encoding = if (mimeType.startsWith("text/") || mimeType == "application/javascript" || mimeType == "application/json") "UTF-8" else null

        // 1. Nếu đã có trong Cache -> Đọc tức thì từ ổ đĩa (0ms)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                Log.d("AgyResourceCache", "Cache HIT: $path")
                return WebResourceResponse(
                    mimeType,
                    encoding,
                    200,
                    "OK",
                    headers,
                    FileInputStream(cacheFile)
                )
            } catch (e: Exception) {
                Log.w("AgyResourceCache", "Lỗi đọc cache file: ${e.message}")
            }
        }

        // 2. Nếu chưa có trong Cache -> Tải ngầm, lưu cache và trả về
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(req).execute()
            if (response.isSuccessful && response.body != null) {
                val bytes = response.body!!.bytes()
                if (bytes.isNotEmpty()) {
                    FileOutputStream(cacheFile).use { fos ->
                        fos.write(bytes)
                    }
                    Log.d("AgyResourceCache", "Cache SAVED: $path (${bytes.size} bytes)")
                    return WebResourceResponse(
                        mimeType,
                        encoding,
                        200,
                        "OK",
                        headers,
                        FileInputStream(cacheFile)
                    )
                }
            }
        } catch (e: Exception) {
            // Không log error vì WebView sẽ tự động fallback sang mạng mặc định
        }

        return null
    }

    private fun isStaticExtension(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".js") ||
                lower.endsWith(".css") ||
                lower.endsWith(".woff2") ||
                lower.endsWith(".woff") ||
                lower.endsWith(".ttf") ||
                lower.endsWith(".svg") ||
                lower.endsWith(".png") ||
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") ||
                lower.endsWith(".webp") ||
                lower.endsWith(".ico")
    }

    private fun getMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".js") -> "application/javascript"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".ico") -> "image/x-icon"
            lower.endsWith(".json") -> "application/json"
            else -> "application/octet-stream"
        }
    }

    private fun hashUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            url.hashCode().toString()
        }
    }

    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e("AgyResourceCache", "Lỗi xóa cache: ${e.message}")
        }
    }
}
