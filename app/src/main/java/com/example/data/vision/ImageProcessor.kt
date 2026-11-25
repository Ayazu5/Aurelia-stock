package com.example.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.model.ImageFingerprint
import com.example.data.model.InventoryItem
import com.example.data.model.PhotoQuality
import com.example.data.model.SimilarCandidate
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Handles bitmap decoding, EXIF orientation correction, adaptive JPEG compression
 * (both for Gemini Vision analysis and Firestore 1MB-safe thumbnails), and 16x16
 * perceptual image fingerprinting for visual variant matching.
 */
object ImageProcessor {

    private const val MAX_THUMBNAIL_BASE64_CHARS = 150_000
    private val bitmapCache = object : LruCache<Int, ImageBitmap>(48) {}

    data class ProcessedPhoto(
        val aiBitmap: Bitmap,
        val thumbnailBase64: String,
        val fingerprint: ImageFingerprint
    )

    fun decodeAndProcessUri(
        context: Context,
        uri: Uri,
        quality: PhotoQuality
    ): ProcessedPhoto? {
        val rawBitmap = decodeSampledBitmapFromUri(context, uri, quality.maxDimension)
            ?: return null
        val oriented = applyExifRotation(context, uri, rawBitmap)
        if (oriented !== rawBitmap) {
            rawBitmap.recycle()
        }
        return processBitmap(oriented, quality)
    }

    fun processBitmap(
        source: Bitmap,
        quality: PhotoQuality
    ): ProcessedPhoto {
        val aiBitmap = scaleDownToMax(source, quality.maxDimension)
        val thumbBase64 = encodeSafeThumbnailBase64(aiBitmap)
        val fingerprint = computeFingerprint(aiBitmap)
        return ProcessedPhoto(
            aiBitmap = aiBitmap,
            thumbnailBase64 = thumbBase64,
            fingerprint = fingerprint
        )
    }

    fun decodeThumbnailToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        val cacheKey = base64.hashCode()
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val clean = base64.substringAfter("base64,", base64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBitmap = bmp.asImageBitmap()
            bitmapCache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compresses a bitmap into a compact JPEG Base64 string guaranteed to remain well below
     * the Firestore rule limit (180,000 chars) and document 1MB budget.
     */
    fun encodeSafeThumbnailBase64(source: Bitmap): String {
        var maxSide = 260
        var jpegQuality = 68
        while (maxSide >= 96) {
            val scaled = scaleDownToMax(source, maxSide)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            if (scaled !== source) {
                scaled.recycle()
            }
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= MAX_THUMBNAIL_BASE64_CHARS) {
                return encoded
            }
            jpegQuality = (jpegQuality - 12).coerceAtLeast(35)
            maxSide = (maxSide * 0.78f).roundToInt()
        }
        val tiny = scaleDownToMax(source, 96)
        val out = ByteArrayOutputStream()
        tiny.compress(Bitmap.CompressFormat.JPEG, 40, out)
        if (tiny !== source) tiny.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            .take(MAX_THUMBNAIL_BASE64_CHARS)
    }

    /**
     * Computes a 16x16 perceptual luminance hash, average RGB, 3-ring radial luminance,
     * and horizontal/vertical edge density for local variant similarity matching.
     */
    fun computeFingerprint(bitmap: Bitmap): ImageFingerprint {
        val size = 16
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small !== bitmap) small.recycle()

        val lums = FloatArray(size * size)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var lumSum = 0f

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            rSum += r
            gSum += g
            bSum += b
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            lums[i] = lum
            lumSum += lum
        }

        val count = pixels.size
        val avgLum = lumSum / count
        val hashBuilder = StringBuilder(count)
        for (lum in lums) {
            hashBuilder.append(if (lum >= avgLum) '1' else '0')
        }

        val ringSums = FloatArray(3)
        val ringCounts = IntArray(3)
        val center = (size - 1) / 2f
        val maxDist = hypot(center.toDouble(), center.toDouble()).toFloat()
        var edgeAccum = 0f
        var edgeCount = 0

        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                val distNorm = hypot((x - center).toDouble(), (y - center).toDouble()).toFloat() / maxDist
                val ring = when {
                    distNorm < 0.34f -> 0
                    distNorm < 0.68f -> 1
                    else -> 2
                }
                ringSums[ring] += lums[idx]
                ringCounts[ring]++

                if (x + 1 < size) {
                    edgeAccum += abs(lums[idx] - lums[idx + 1])
                    edgeCount++
                }
                if (y + 1 < size) {
                    edgeAccum += abs(lums[idx] - lums[idx + size])
                    edgeCount++
                }
            }
        }

        val avgRgb = listOf(
            (rSum / count).coerceIn(0L, 255L),
            (gSum / count).coerceIn(0L, 255L),
            (bSum / count).coerceIn(0L, 255L)
        )
        val radial = (0..2).map { i ->
            if (ringCounts[i] > 0) (ringSums[i] / ringCounts[i]).roundToInt().toLong().coerceIn(0L, 255L)
            else 0L
        }
        val edgeDensity = if (edgeCount > 0) {
            (edgeAccum / edgeCount).roundToInt().toLong().coerceIn(0L, 512L)
        } else 0L

        return ImageFingerprint(
            hash = hashBuilder.toString(),
            avgRgb = avgRgb,
            radial = radial,
            edgeDensity = edgeDensity
        )
    }

    fun compareFingerprints(a: ImageFingerprint?, b: ImageFingerprint?): Double {
        if (a == null || b == null || a.hash.length != b.hash.length || a.hash.isEmpty()) return 0.0
        var sameBits = 0
        for (i in a.hash.indices) {
            if (a.hash[i] == b.hash[i]) sameBits++
        }
        val hashSim = sameBits.toDouble() / a.hash.length.toDouble()

        val rgbDiff = (0..2).sumOf { i ->
            abs((a.avgRgb.getOrElse(i) { 0L }) - (b.avgRgb.getOrElse(i) { 0L }))
        }
        val rgbSim = max(0.0, 1.0 - rgbDiff / (255.0 * 3.0))

        val radDiff = (0..2).sumOf { i ->
            abs((a.radial.getOrElse(i) { 0L }) - (b.radial.getOrElse(i) { 0L }))
        }
        val radSim = max(0.0, 1.0 - radDiff / (255.0 * 3.0))

        val edgeSim = max(0.0, 1.0 - abs(a.edgeDensity - b.edgeDensity) / 140.0)

        return (hashSim * 0.45) + (rgbSim * 0.25) + (radSim * 0.20) + (edgeSim * 0.10)
    }

    fun findLocalCandidates(
        fingerprint: ImageFingerprint?,
        items: List<InventoryItem>,
        minScore: Double = 0.68
    ): List<SimilarCandidate> {
        if (fingerprint == null || items.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val itemFp = item.toImageFingerprint() ?: return@mapNotNull null
            val sim = compareFingerprints(fingerprint, itemFp)
            if (sim >= minScore) {
                SimilarCandidate(
                    id = item.id,
                    similarity = sim,
                    reason = "Visual fingerprint similarity (${(sim * 100).roundToInt()}%)"
                )
            } else null
        }.sortedByDescending { it.similarity }.take(4)
    }

    fun scaleDownToMax(bitmap: Bitmap, maxSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest.toFloat()
        val newW = max(1, (width * ratio).roundToInt())
        val newH = max(1, (height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqMaxSide: Int
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) return null

            var inSampleSize = 1
            val longest = max(origW, origH)
            while (longest / (inSampleSize * 2) >= reqMaxSide) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    fun normalizeOcrText(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
