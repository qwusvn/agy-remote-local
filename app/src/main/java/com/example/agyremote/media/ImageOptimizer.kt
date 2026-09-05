package com.example.agyremote.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageOptimizer {

    private const val MAX_DIMENSION = 1920 // Độ phân giải tối đa cho AI đọc code/text rõ nét
    private const val COMPRESSION_QUALITY = 82 // Chất lượng nén cân bằng hoàn hảo

    /**
     * Tối ưu hóa ảnh từ Uri (Gallery / Camera): Downscale, xoay chuẩn Exif và nén thành file tạm
     */
    suspend fun optimizeImage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            // 1. Đọc kích thước ban đầu mà không load toàn bộ bitmap vào RAM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return@withContext null

            // 2. Tính toán tỷ lệ mẫu (inSampleSize)
            options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, MAX_DIMENSION, MAX_DIMENSION)
            options.inJustDecodeBounds = false

            // 3. Đọc bitmap với kích thước đã downscale
            val loadedBitmap: Bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@withContext null

            // 4. Xử lý góc xoay EXIF nếu ảnh từ Camera
            var bitmap: Bitmap = rotateBitmapIfNeeded(context, sourceUri, loadedBitmap)

            // 5. Nếu kích thước vẫn lớn hơn MAX_DIMENSION, scale mượt về đúng kích thước chuẩn
            val currentMax = max(bitmap.width, bitmap.height)
            if (currentMax > MAX_DIMENSION) {
                val scaleRatio = MAX_DIMENSION.toFloat() / currentMax
                val targetW = (bitmap.width * scaleRatio).toInt()
                val targetH = (bitmap.height * scaleRatio).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // 6. Lưu vào file tạm thời trong cache
            val outputDir = File(context.cacheDir, "agy_uploads").apply { mkdirs() }
            val outputFile = File(outputDir, "upload_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outputFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
            }
            bitmap.recycle()

            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Chuyển đổi Uri thành chuỗi Base64 và MIME type để inject trực tiếp vào WebView
     */
    suspend fun getBase64Image(context: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Pair(base64, mimeType)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cleanupOldUploads(context: Context) {
        try {
            val dir = File(context.cacheDir, "agy_uploads")
            if (dir.exists()) {
                val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < dayAgo) file.delete()
                }
            }
        } catch (e: Exception) {}
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        val maxDim = max(width, height)
        val reqMaxDim = max(reqWidth, reqHeight)

        if (maxDim > reqMaxDim) {
            val halfMax = maxDim / 2
            while ((halfMax / inSampleSize) >= reqMaxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmapIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            if (input != null) {
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                input.close()

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }

                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }
                return rotatedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bitmap
    }
}
