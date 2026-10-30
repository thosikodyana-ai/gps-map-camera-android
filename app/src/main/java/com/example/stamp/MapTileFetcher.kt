package com.example.stamp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

object WebMercatorMath {
    fun latLonToExactTile(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val clampedLon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val clampedLat = latitude.coerceIn(-85.05112878, 85.05112878)
        val x = (clampedLon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(clampedLat)
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return Pair(x, y)
    }

    fun exactTileToLatLon(tileX: Double, tileY: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val lonDeg = (tileX / n) * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * tileY / n)))
        val latDeg = Math.toDegrees(latRad).coerceIn(-85.05112878, 85.05112878)
        val normalizedLon = (((lonDeg + 180.0) % 360.0 + 360.0) % 360.0) - 180.0
        return Pair(latDeg, normalizedLon)
    }
}

class MapTileFetcher(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val memoryCache = ConcurrentHashMap<String, Bitmap>()

    suspend fun getMiniMapBitmap(
        latitude: Double,
        longitude: Double,
        isSatellite: Boolean = true,
        streetHint: String = "",
        sizePx: Int = 280
    ): Bitmap = withContext(Dispatchers.IO) {
        val zoom = 16
        val (exactX, exactY) = WebMercatorMath.latLonToExactTile(latitude, longitude, zoom)
        val n = 2.0.pow(zoom.toDouble()).toInt()

        val tileX = floor(exactX).toInt().coerceIn(0, n - 1)
        val tileY = floor(exactY).toInt().coerceIn(0, n - 1)

        val rawTile = fetchRawTile(zoom, tileX, tileY, isSatellite)
        if (isSatellite) {
            renderTemplate1SatelliteMiniMap(
                baseTile = rawTile,
                sizePx = sizePx,
                latitude = latitude,
                longitude = longitude
            )
        } else {
            renderTemplate2StreetMiniMap(
                baseTile = rawTile,
                sizePx = sizePx,
                latitude = latitude,
                longitude = longitude,
                streetHint = streetHint
            )
        }
    }

    suspend fun fetchTileForInteractiveMap(
        zoom: Int,
        tileX: Int,
        tileY: Int,
        isSatellite: Boolean
    ): Bitmap? = withContext(Dispatchers.IO) {
        val maxTile = (1 shl zoom) - 1
        val wrappedX = ((tileX % (maxTile + 1)) + (maxTile + 1)) % (maxTile + 1)
        if (tileY < 0 || tileY > maxTile) return@withContext null
        fetchRawTile(zoom, wrappedX, tileY, isSatellite)
    }

    private fun fetchRawTile(zoom: Int, tileX: Int, tileY: Int, isSatellite: Boolean): Bitmap? {
        val stylePrefix = if (isSatellite) "sat" else "osm"
        val cacheKey = "${stylePrefix}_${zoom}_${tileX}_${tileY}"
        memoryCache[cacheKey]?.let { if (!it.isRecycled) return it }

        val cacheDir = File(context.cacheDir, "map_tiles").apply { mkdirs() }
        val diskFile = File(cacheDir, "$cacheKey.png")
        if (diskFile.exists() && diskFile.length() > 100) {
            BitmapFactory.decodeFile(diskFile.absolutePath)?.let { bmp ->
                memoryCache[cacheKey] = bmp
                return bmp
            }
        }

        return runCatching {
            val url = if (isSatellite) {
                // Esri World Imagery uses z/y/x order
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$tileY/$tileX"
            } else {
                "https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png"
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPSMapCameraAndroid/1.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                runCatching {
                    FileOutputStream(diskFile).use { out ->
                        out.write(bytes)
                    }
                }
                memoryCache[cacheKey] = bmp
                bmp
            }
        }.getOrNull()
    }

    /**
     * Renders the exact Mini-Map style of Reference Image 1 (Brebes Satellite):
     * - Aerial satellite terrain texture
     * - Red teardrop pin in the center with dark inner dot
     * - White "Google" text with dark outline at the bottom center
     */
    private fun renderTemplate1SatelliteMiniMap(
        baseTile: Bitmap?,
        sizePx: Int,
        latitude: Double,
        longitude: Double
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (baseTile != null) {
            canvas.drawBitmap(
                baseTile,
                Rect(0, 0, baseTile.width, baseTile.height),
                Rect(0, 0, sizePx, sizePx),
                paint
            )
        } else {
            drawProceduralSatelliteTerrain(canvas, sizePx, latitude, longitude)
        }

        // Red Teardrop Pin in center (matching Image 1)
        drawTeardropMapPin(
            canvas = canvas,
            centerX = sizePx * 0.50f,
            tipY = sizePx * 0.55f,
            pinHeight = sizePx * 0.22f
        )

        // White "Google" text with dark stroke at bottom center (matching Image 1)
        val textSize = sizePx * 0.175f
        val textY = sizePx * 0.89f
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 22, 24, 28)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.028f
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.FILL
        }
        canvas.drawText("Google", sizePx * 0.50f, textY, strokePaint)
        canvas.drawText("Google", sizePx * 0.50f, textY, fillPaint)

        return output
    }

    /**
     * Renders the exact Mini-Map style of Reference Image 2 (Street Map + Multicolored Google):
     * - Light street map background with building blocks, blue-gray avenues & angled street labels
     * - Prominent red teardrop pin in center
     * - Multicolored "Google" logo at the bottom-left/center
     */
    private fun renderTemplate2StreetMiniMap(
        baseTile: Bitmap?,
        sizePx: Int,
        latitude: Double,
        longitude: Double,
        streetHint: String
    ): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (baseTile != null) {
            canvas.drawBitmap(
                baseTile,
                Rect(0, 0, baseTile.width, baseTile.height),
                Rect(0, 0, sizePx, sizePx),
                paint
            )
        } else {
            drawProceduralLightStreetMap(canvas, sizePx, latitude, longitude, streetHint)
        }

        // Large Red Teardrop Pin in center (matching Image 2)
        drawTeardropMapPin(
            canvas = canvas,
            centerX = sizePx * 0.49f,
            tipY = sizePx * 0.56f,
            pinHeight = sizePx * 0.28f
        )

        // Multicolored "Google" logo at bottom (matching Image 2)
        drawMulticoloredGoogleLogo(
            canvas = canvas,
            startX = sizePx * 0.14f,
            baselineY = sizePx * 0.90f,
            textSize = sizePx * 0.155f
        )

        return output
    }

    private fun drawTeardropMapPin(
        canvas: Canvas,
        centerX: Float,
        tipY: Float,
        pinHeight: Float
    ) {
        val radius = pinHeight * 0.38f
        val circleCenterY = tipY - pinHeight * 0.62f

        // Drop shadow under pin tip
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, tipY, radius * 0.25f, shadowPaint)

        // Teardrop silhouette path
        val pinPath = Path().apply {
            moveTo(centerX, tipY)
            cubicTo(
                centerX - radius * 0.45f, tipY - pinHeight * 0.28f,
                centerX - radius, circleCenterY + radius * 0.35f,
                centerX - radius, circleCenterY
            )
            arcTo(
                centerX - radius,
                circleCenterY - radius,
                centerX + radius,
                circleCenterY + radius,
                180f,
                180f,
                false
            )
            cubicTo(
                centerX + radius, circleCenterY + radius * 0.35f,
                centerX + radius * 0.45f, tipY - pinHeight * 0.28f,
                centerX, tipY
            )
            close()
        }

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(234, 67, 53) // Google Pin Red #EA4335
            style = Paint.Style.FILL
        }
        val pinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 38, 28)
            style = Paint.Style.STROKE
            strokeWidth = (pinHeight * 0.03f).coerceAtLeast(1.5f)
        }
        canvas.drawPath(pinPath, pinPaint)
        canvas.drawPath(pinPath, pinStrokePaint)

        // Dark crimson/black inner circle inside pin head
        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(122, 24, 18)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, circleCenterY, radius * 0.38f, innerDotPaint)
    }

    private fun drawMulticoloredGoogleLogo(
        canvas: Canvas,
        startX: Float,
        baselineY: Float,
        textSize: Float
    ) {
        val letters = listOf(
            'G' to Color.rgb(66, 133, 244),  // Blue
            'o' to Color.rgb(234, 67, 53),   // Red
            'o' to Color.rgb(251, 188, 5),   // Yellow
            'g' to Color.rgb(66, 133, 244),  // Blue
            'l' to Color.rgb(52, 168, 83),   // Green
            'e' to Color.rgb(234, 67, 53)    // Red
        )

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.16f
            strokeJoin = Paint.Join.ROUND
        }
        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.FILL
        }

        var cursorX = startX
        for ((ch, color) in letters) {
            val s = ch.toString()
            canvas.drawText(s, cursorX, baselineY, haloPaint)
            letterPaint.color = color
            canvas.drawText(s, cursorX, baselineY, letterPaint)
            cursorX += letterPaint.measureText(s)
        }
    }

    private fun drawProceduralSatelliteTerrain(
        canvas: Canvas,
        sizePx: Int,
        latitude: Double,
        longitude: Double
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Base earth & greenery gradient
        paint.shader = LinearGradient(
            0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
            Color.rgb(44, 68, 48),
            Color.rgb(62, 74, 58),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        paint.shader = null

        val seed = ((latitude * 10000).toInt() xor (longitude * 10000).toInt())
        val roofColors = intArrayOf(
            Color.rgb(158, 92, 68),  // Terracotta roof
            Color.rgb(176, 108, 82), // Light clay roof
            Color.rgb(132, 140, 146),// Concrete roof
            Color.rgb(52, 86, 56),   // Tree canopy
            Color.rgb(88, 108, 76),  // Field parcel
            Color.rgb(190, 178, 162) // Courtyard
        )

        val cellCount = 9
        val cellSize = sizePx.toFloat() / cellCount
        for (r in 0 until cellCount) {
            for (c in 0 until cellCount) {
                val hash = kotlin.math.abs((r * 31 + c * 17 + seed) % roofColors.size)
                paint.color = roofColors[hash]
                val pad = cellSize * 0.08f
                canvas.drawRect(
                    c * cellSize + pad,
                    r * cellSize + pad,
                    (c + 1) * cellSize - pad,
                    (r + 1) * cellSize - pad,
                    paint
                )
            }
        }

        // Asphalt main vertical & horizontal roads matching satellite view
        paint.color = Color.rgb(78, 84, 90)
        canvas.drawRect(sizePx * 0.56f, 0f, sizePx * 0.66f, sizePx.toFloat(), paint)
        canvas.drawRect(0f, sizePx * 0.44f, sizePx.toFloat(), sizePx * 0.52f, paint)
    }

    private fun drawProceduralLightStreetMap(
        canvas: Canvas,
        sizePx: Int,
        latitude: Double,
        longitude: Double,
        streetHint: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Light Google Maps land background #F1F3F4
        paint.color = Color.rgb(241, 243, 244)
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)

        // Subtle warm building footprints (#FAF6EE)
        paint.color = Color.rgb(248, 241, 226)
        canvas.drawRect(sizePx * 0.05f, sizePx * 0.08f, sizePx * 0.36f, sizePx * 0.34f, paint)
        canvas.drawRect(sizePx * 0.58f, sizePx * 0.10f, sizePx * 0.92f, sizePx * 0.42f, paint)
        canvas.drawRect(sizePx * 0.12f, sizePx * 0.56f, sizePx * 0.48f, sizePx * 0.84f, paint)
        canvas.drawRect(sizePx * 0.62f, sizePx * 0.60f, sizePx * 0.94f, sizePx * 0.88f, paint)

        // Secondary roads (light blue-gray stroke + white fill)
        val roadCasing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 222, 238)
            strokeWidth = sizePx * 0.095f
            style = Paint.Style.STROKE
        }
        val roadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = sizePx * 0.070f
            style = Paint.Style.STROKE
        }

        // Diagonal main street like Reference Image 2
        canvas.drawLine(sizePx * 0.06f, 0f, sizePx * 0.96f, sizePx * 0.72f, roadCasing)
        canvas.drawLine(sizePx * 0.18f, 0f, sizePx * 0.14f, sizePx.toFloat(), roadCasing)
        canvas.drawLine(0f, sizePx * 0.82f, sizePx.toFloat(), sizePx * 0.78f, roadCasing)

        canvas.drawLine(sizePx * 0.06f, 0f, sizePx * 0.96f, sizePx * 0.72f, roadFill)
        canvas.drawLine(sizePx * 0.18f, 0f, sizePx * 0.14f, sizePx.toFloat(), roadFill)
        canvas.drawLine(0f, sizePx * 0.82f, sizePx.toFloat(), sizePx * 0.78f, roadFill)

        // Angled street label along diagonal road
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(74, 85, 104)
            textSize = sizePx * 0.072f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val label = streetHint.ifBlank { "Jl. Utama" }.take(18)
        canvas.save()
        canvas.translate(sizePx * 0.14f, sizePx * 0.12f)
        canvas.rotate(38f)
        canvas.drawText(label, 0f, 0f, labelPaint)
        canvas.restore()
    }
}
