package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ProductImageStorage {

    const val USER_AGENT = "SmartBusinessManagerApp/1.0 (Android; support@smartbiz.local)"
    private const val PRODUCT_IMAGES_DIR = "product_images"
    private const val TEMP_IMAGES_DIR = "temp_images"
    private const val MAX_DIMENSION = 1024

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, PRODUCT_IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, TEMP_IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Create a FileProvider Uri for camera capture.
     */
    fun createTempImageUriForCamera(context: Context): Pair<Uri, File> {
        val tempDir = getTempDir(context)
        val file = File(tempDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(uri, file)
    }

    /**
     * Copy an external content Uri (from Photo Picker / Gallery) into app's private filesDir.
     * Resizes if larger than 1024px to conserve memory and disk.
     */
    suspend fun copyUriToInternalStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(sourceUri) ?: return@withContext null
            
            // Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size
            var inSampleSize = 1
            val (width, height) = options.outWidth to options.outHeight
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / inSampleSize >= MAX_DIMENSION && halfWidth / inSampleSize >= MAX_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            // Decode bitmap with sample size
            val decodeStream = contentResolver.openInputStream(sourceUri) ?: return@withContext null
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            var bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            if (bitmap == null) return@withContext null

            // Correct EXIF orientation if needed
            try {
                contentResolver.openInputStream(sourceUri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    bitmap = rotateBitmapIfRequired(bitmap!!, orientation)
                }
            } catch (_: Exception) { }

            saveBitmapInternal(context, bitmap!!, "user")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a Bitmap (e.g. from Camera or AI/Search generation) into internal storage.
     */
    suspend fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, prefix: String = "img"): String? =
        withContext(Dispatchers.IO) {
            saveBitmapInternal(context, bitmap, prefix)
        }

    private fun saveBitmapInternal(context: Context, bitmap: Bitmap, prefix: String): String? {
        return try {
            val storageDir = getStorageDir(context)
            val fileName = "${prefix}_${System.currentTimeMillis()}_${(100..999).random()}.jpg"
            val file = File(storageDir, fileName)

            // Scale down if still larger than max dimension
            val scaledBitmap = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                val ratio = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resolves an image path or URI to an existing File, content URI, or network URL.
     * Handles differences in package names, absolute paths across devices, or relative file names.
     */
    fun resolveImageModel(context: Context, imageUri: String?): Any? {
        if (imageUri.isNullOrBlank()) return null
        if (imageUri.startsWith("content://") || imageUri.startsWith("http://") || imageUri.startsWith("https://")) {
            return imageUri
        }
        val directFile = File(imageUri)
        if (directFile.exists() && directFile.length() > 0) {
            return directFile
        }

        // Search by file name in app private storage directories
        val fileName = directFile.name
        val inStorage = File(getStorageDir(context), fileName)
        if (inStorage.exists() && inStorage.length() > 0) {
            return inStorage
        }

        val inFilesDir = File(context.filesDir, fileName)
        if (inFilesDir.exists() && inFilesDir.length() > 0) {
            return inFilesDir
        }

        val inCacheDir = File(context.cacheDir, fileName)
        if (inCacheDir.exists() && inCacheDir.length() > 0) {
            return inCacheDir
        }

        val inTempDir = File(getTempDir(context), fileName)
        if (inTempDir.exists() && inTempDir.length() > 0) {
            return inTempDir
        }

        return directFile
    }

    /**
     * Builds a Coil ImageRequest with proper User-Agent header and crossfade.
     * Prevents HTTP 403 Forbidden on Wikimedia Commons and external CDNs.
     */
    fun buildImageRequest(context: Context, rawModel: Any?): ImageRequest {
        val resolved = if (rawModel is String) resolveImageModel(context, rawModel) else rawModel
        val builder = ImageRequest.Builder(context)
            .data(resolved)
            .crossfade(true)
        if (resolved is String && (resolved.startsWith("http://") || resolved.startsWith("https://"))) {
            builder.setHeader("User-Agent", USER_AGENT)
        }
        return builder.build()
    }

    /**
     * Deletes an image file from internal storage if safe.
     */
    suspend fun deleteImageFile(filePath: String?) = withContext(Dispatchers.IO) {
        if (filePath.isNullOrBlank()) return@withContext
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (_: Exception) { }
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
