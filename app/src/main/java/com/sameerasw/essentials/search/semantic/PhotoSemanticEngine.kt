package com.sameerasw.essentials.search.semantic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

data class PhotoResultItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val bucketName: String,
    val dateTaken: Long,
)

object PhotoSemanticEngine {
    private const val EMBEDDING_DIM = 64

    // Semantic concept anchors mapping visual color/luminance/aspect/OCR/album traits
    private val CONCEPT_VOCAB = listOf(
        "sunset", "sunrise", "sky", "blue", "ocean", "sea", "beach", "water",
        "nature", "forest", "green", "tree", "grass", "plant", "flower",
        "night", "dark", "moon", "city", "neon", "light", "bright", "snow", "white",
        "warm", "red", "orange", "yellow", "gold", "food", "coffee", "restaurant",
        "screenshot", "document", "receipt", "text", "note", "code", "screen",
        "portrait", "selfie", "person", "face", "indoor", "room",
        "photo", "camera", "vacation", "travel", "summer", "winter",
    )

    fun hasCustomMobileClipModel(context: Context): Boolean {
        val dir = File(context.filesDir, "models")
        return File(dir, "mobileclip_image.tflite").exists() && File(dir, "mobileclip_text.tflite").exists()
    }

    suspend fun getIndexedCount(context: Context): Int = withContext(Dispatchers.IO) {
        runCatching { PhotoIndexDatabase.getInstance(context).photoIndexDao().count() }.getOrDefault(0)
    }

    suspend fun clearIndex(context: Context) = withContext(Dispatchers.IO) {
        PhotoIndexDatabase.getInstance(context).photoIndexDao().clearAll()
    }

    suspend fun indexBatch(context: Context, maxToIndex: Int = 120): Int = withContext(Dispatchers.IO) {
        val dao = PhotoIndexDatabase.getInstance(context).photoIndexDao()
        val existingIds = dao.getAllIds().toHashSet()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val pending = mutableListOf<PhotoResultItem>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                while (cursor.moveToNext() && pending.size < maxToIndex) {
                    val id = cursor.getLong(idIdx)
                    if (existingIds.contains(id)) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    pending.add(
                        PhotoResultItem(
                            id = id,
                            contentUri = uri.toString(),
                            displayName = cursor.getString(nameIdx) ?: "Photo",
                            bucketName = cursor.getString(bucketIdx) ?: "",
                            dateTaken = cursor.getLong(dateIdx),
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext 0
        }

        if (pending.isEmpty()) return@withContext 0

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val entities = mutableListOf<PhotoIndexEntity>()

        try {
            for (item in pending) {
                val uri = Uri.parse(item.contentUri)
                val bitmap = decodeSampledBitmap(context, uri, 256)
                val ocr: String
                val visualTags: List<String>
                val vec: FloatArray
                try {
                    ocr = if (bitmap != null) runOcr(recognizer, bitmap) else ""
                    visualTags = if (bitmap != null) deriveVisualTags(bitmap, item, ocr) else emptyList()
                    vec = computePhotoVector(bitmap, item, ocr, visualTags)
                } finally {
                    bitmap?.recycle()
                }

                entities.add(
                    PhotoIndexEntity(
                        mediaId = item.id,
                        contentUri = item.contentUri,
                        displayName = item.displayName,
                        bucketName = item.bucketName,
                        dateTaken = item.dateTaken,
                        ocrText = ocr,
                        sceneTags = visualTags.joinToString(" "),
                        embeddingBlob = PhotoIndexEntity.fromFloatArray(vec),
                        indexedAt = System.currentTimeMillis(),
                    ),
                )
            }
        } finally {
            runCatching { recognizer.close() }
        }

        dao.upsertAll(entities)
        entities.size
    }

    suspend fun searchSemanticPhotos(
        context: Context,
        query: String,
        limit: Int = 60,
    ): List<PhotoResultItem> = withContext(Dispatchers.IO) {
        val dao = PhotoIndexDatabase.getInstance(context).photoIndexDao()
        val indexed = dao.getAll()
        if (indexed.isEmpty()) return@withContext emptyList()

        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return@withContext indexed.take(limit).map {
                PhotoResultItem(
                    id = it.mediaId,
                    contentUri = it.contentUri,
                    displayName = it.displayName,
                    bucketName = it.bucketName,
                    dateTaken = it.dateTaken,
                )
            }
        }

        val queryVec = encodeTextQuery(q)
        val terms = q.split(Regex("\\s+")).filter { it.length >= 2 }

        indexed.mapNotNull { entity ->
            val vec = entity.toFloatArray()
            val cos = cosineSimilarity(queryVec, vec)
            val ocrLower = entity.ocrText.lowercase()
            val tagsLower = entity.sceneTags.lowercase()
            val nameLower = entity.displayName.lowercase()
            val bucketLower = entity.bucketName.lowercase()
            val hay = "$nameLower $bucketLower $ocrLower $tagsLower"
            var lexicalBoost = 0f
            for (t in terms) {
                if (ocrLower.contains(t)) lexicalBoost += 0.55f
                else if (tagsLower.contains(t)) lexicalBoost += 0.40f
                else if (hay.contains(t)) lexicalBoost += 0.30f
            }
            val score = cos + lexicalBoost
            if (score < 0.20f) null
            else entity to score
        }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (e, _) ->
                PhotoResultItem(
                    id = e.mediaId,
                    contentUri = e.contentUri,
                    displayName = e.displayName,
                    bucketName = e.bucketName,
                    dateTaken = e.dateTaken,
                )
            }
    }

    private suspend fun runOcr(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text.take(600))
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume("")
            }
            .addOnCanceledListener {
                if (cont.isActive) cont.resume("")
            }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, targetSize: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (maxDim / sample > targetSize * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    private fun deriveVisualTags(bitmap: Bitmap, item: PhotoResultItem, ocr: String): List<String> {
        val tags = mutableSetOf<String>()
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return emptyList()

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var brightSum = 0L
        var count = 0L
        var blueCount = 0L
        var greenCount = 0L
        var warmCount = 0L
        var whiteCount = 0L
        var darkCount = 0L

        val stepX = (w / 24).coerceAtLeast(1)
        val stepY = (h / 24).coerceAtLeast(1)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                rSum += r; gSum += g; bSum += b; brightSum += lum
                if (b > r + 20 && b > g + 5) blueCount++
                if (g > r + 15 && g > b + 15) greenCount++
                if (r > b + 35 && r > 120) warmCount++
                if (lum > 225) whiteCount++
                if (lum < 50) darkCount++
                count++
                x += stepX
            }
            y += stepY
        }

        val c = count.coerceAtLeast(1)
        val avgLum = brightSum / c
        if (avgLum < 70 || darkCount * 100 / c > 55) {
            tags.addAll(listOf("night", "dark", "moon"))
        }
        if (avgLum > 195 || whiteCount * 100 / c > 50) {
            tags.addAll(listOf("bright", "white", "snow", "document"))
        }
        if (blueCount * 100 / c > 25) {
            tags.addAll(listOf("blue", "sky", "ocean", "sea", "water", "beach"))
        }
        if (greenCount * 100 / c > 20) {
            tags.addAll(listOf("green", "nature", "forest", "tree", "grass", "plant"))
        }
        if (warmCount * 100 / c > 25) {
            tags.addAll(listOf("warm", "sunset", "sunrise", "orange", "gold", "red", "food"))
        }

        val bucket = item.bucketName.lowercase()
        val name = item.displayName.lowercase()
        if (bucket.contains("screenshot") || name.contains("screenshot")) {
            tags.addAll(listOf("screenshot", "screen"))
        }
        if (ocr.length > 25) {
            tags.addAll(listOf("text", "document", "note", "receipt", "screen"))
            if (ocr.contains("$") || ocr.contains("total", true) || ocr.contains("tax", true)) {
                tags.add("receipt")
            }
            if (ocr.contains("{") && ocr.contains("}") && (ocr.contains("fun ") || ocr.contains("const ") || ocr.contains("import "))) {
                tags.add("code")
            }
        }
        if (h > w * 1.25f) tags.add("portrait")
        if (item.dateTaken > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateTaken }
            when (cal.get(Calendar.MONTH)) {
                Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> tags.add("summer")
                Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> tags.add("winter")
            }
        }
        return tags.toList()
    }

    private fun computePhotoVector(
        bitmap: Bitmap?,
        item: PhotoResultItem,
        ocr: String,
        tags: List<String>,
    ): FloatArray {
        val vec = FloatArray(EMBEDDING_DIM)
        val combined = (tags.joinToString(" ") + " " + item.bucketName + " " + item.displayName + " " + ocr).lowercase()
        CONCEPT_VOCAB.forEachIndexed { idx, concept ->
            if (idx < EMBEDDING_DIM && combined.contains(concept)) {
                vec[idx] += 1.0f
            }
        }
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val px = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            vec[EMBEDDING_DIM - 4] = Color.red(px) / 255f
            vec[EMBEDDING_DIM - 3] = Color.green(px) / 255f
            vec[EMBEDDING_DIM - 2] = Color.blue(px) / 255f
            vec[EMBEDDING_DIM - 1] = if (bitmap.height > bitmap.width) 1f else 0f
        }
        normalize(vec)
        return vec
    }

    private fun encodeTextQuery(query: String): FloatArray {
        val vec = FloatArray(EMBEDDING_DIM)
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        CONCEPT_VOCAB.forEachIndexed { idx, concept ->
            if (idx < EMBEDDING_DIM && (query.contains(concept) || tokens.any { concept.startsWith(it) || it.startsWith(concept) })) {
                vec[idx] += 1.0f
            }
        }
        // Hash fallback for arbitrary terms
        for (t in tokens) {
            val bucket = abs(t.hashCode()) % (EMBEDDING_DIM - 4)
            vec[bucket] += 0.6f
        }
        normalize(vec)
        return vec
    }

    private fun normalize(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm > 1e-6f) {
            for (i in v.indices) v[i] /= norm
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val len = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until len) dot += a[i] * b[i]
        return dot
    }
}
