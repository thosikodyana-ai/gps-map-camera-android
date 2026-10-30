package com.example.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.LocationMetadata
import com.example.model.StampPosition
import com.example.model.StampStyleConfig
import com.example.model.StampTemplate
import com.example.model.TimeConfig
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapStampRenderer {

    fun renderStampedBitmap(
        sourceBitmap: Bitmap,
        timeConfig: TimeConfig,
        location: LocationMetadata,
        styleConfig: StampStyleConfig,
        miniMapBitmap: Bitmap?
    ): Bitmap {
        val width = sourceBitmap.width.coerceAtLeast(640)
        val height = sourceBitmap.height.coerceAtLeast(480)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw original photo
        canvas.drawBitmap(sourceBitmap, 0f, 0f, null)

        when (styleConfig.template) {
            StampTemplate.CLASSIC -> drawReferenceTemplate1Classic(
                canvas = canvas,
                width = width,
                height = height,
                timeConfig = timeConfig,
                location = location,
                styleConfig = styleConfig,
                miniMapBitmap = miniMapBitmap
            )
            StampTemplate.MODERN -> drawReferenceTemplate2Telemetry(
                canvas = canvas,
                width = width,
                height = height,
                timeConfig = timeConfig,
                location = location,
                styleConfig = styleConfig,
                miniMapBitmap = miniMapBitmap
            )
            StampTemplate.MINIMALIST -> drawMinimalistStamp(
                canvas = canvas,
                width = width,
                height = height,
                timeConfig = timeConfig,
                location = location,
                styleConfig = styleConfig,
                miniMapBitmap = miniMapBitmap
            )
        }

        return output
    }

    /**
     * 100% Pixel-Accurate Renderer for Reference Image 1 (Brebes Satellite Style):
     * - Separate Left Rounded Card: Satellite Mini-Map with red pin & white "Google" text
     * - Top-Right Attached Tab Badge: "[📷] GPS Map Camera"
     * - Separate Right Rounded Card:
     *   - Headline (2 lines max): "Kecamatan Brebes, Jawa Tengah,\nIndonesia 🇮🇩"
     *   - Plus Code + Full Address: "427p+2cv, Jl. Sapphire Regency, Pulosari, ..."
     *   - Coordinates: "Lat -6.887405° Long 109.035938°"
     *   - Timestamp: "Jumat, 30/10/2026 01:47 PM GMT +07:00"
     *   - Note: "Note : Captured by GPS Map Camera"
     */
    private fun drawReferenceTemplate1Classic(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeConfig: TimeConfig,
        location: LocationMetadata,
        styleConfig: StampStyleConfig,
        miniMapBitmap: Bitmap?
    ) {
        val baseUnit = min(width, height).toFloat()
        val outerMargin = baseUnit * 0.026f
        val gapBetweenCards = baseUnit * 0.018f
        val cardRadius = baseUnit * 0.018f
        val innerPadX = baseUnit * 0.022f
        val innerPadY = baseUnit * 0.018f

        val totalAvailableWidth = width - outerMargin * 2f
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null
        val mapCardWidth = if (showMap) totalAvailableWidth * 0.265f else 0f
        val infoCardLeft = if (showMap) outerMargin + mapCardWidth + gapBetweenCards else outerMargin
        val infoCardRight = width - outerMargin
        val infoCardWidth = infoCardRight - infoCardLeft
        val textMaxWidth = (infoCardWidth - innerPadX * 2f).toInt().coerceAtLeast(180)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseUnit * 0.035f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 248)
            textSize = baseUnit * 0.0225f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val headlineText = location.template1Headline
        val headlineLayout = createStaticLayout(headlineText, titlePaint, textMaxWidth, maxLines = 2)
        val addressLayout = createStaticLayout(
            location.template1FullAddressWithPlusCode,
            bodyPaint,
            textMaxWidth,
            maxLines = 3
        )

        val latLongLine = location.template1LatLongText
        val dateTimeLine = timeConfig.formatTemplate1Timestamp()
        val noteLine = if (styleConfig.showProjectNote && location.projectNote.isNotBlank()) {
            if (location.projectNote.startsWith("Note", ignoreCase = true)) {
                location.projectNote
            } else {
                "Note : ${location.projectNote}"
            }
        } else {
            ""
        }

        val lineGap = baseUnit * 0.0065f
        val bodyLineHeight = bodyPaint.textSize * 1.24f
        var contentHeight = innerPadY * 2f +
            headlineLayout.height + lineGap * 1.2f +
            addressLayout.height + lineGap +
            bodyLineHeight * 2f

        if (noteLine.isNotEmpty()) {
            contentHeight += bodyLineHeight
        }

        val cardHeight = contentHeight.coerceAtLeast(if (showMap) mapCardWidth * 0.96f else contentHeight)
        val badgeTabHeight = if (styleConfig.showBrandBadgeTab) baseUnit * 0.044f else 0f

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            height - outerMargin - cardHeight
        } else {
            outerMargin + badgeTabHeight
        }
        val cardBottom = cardTop + cardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 42, 44, 48)
            style = Paint.Style.FILL
        }

        // 1. Draw Top-Right Attached Tab Badge "[📷] GPS Map Camera" (matching Image 1)
        if (styleConfig.showBrandBadgeTab) {
            val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = baseUnit * 0.021f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val label = "GPS Map Camera"
            val iconSize = badgeTabHeight * 0.58f
            val tabPadX = baseUnit * 0.014f
            val tabWidth = tabPadX * 2f + iconSize + baseUnit * 0.010f + badgeTextPaint.measureText(label)
            val tabLeft = (infoCardRight - tabWidth).coerceAtLeast(infoCardLeft)
            val tabTop = cardTop - badgeTabHeight + cardRadius * 0.25f
            val tabRect = RectF(tabLeft, tabTop, infoCardRight, cardTop + cardRadius)

            canvas.drawRoundRect(tabRect, cardRadius * 0.75f, cardRadius * 0.75f, cardBgPaint)

            // Small Camera App Icon inside badge tab
            val iconLeft = tabLeft + tabPadX
            val iconTop = tabTop + (badgeTabHeight - iconSize) * 0.46f
            val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(38, 166, 154)
            }
            canvas.drawRoundRect(iconRect, iconSize * 0.22f, iconSize * 0.22f, iconBgPaint)
            val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 193, 7)
            }
            canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.28f, lensPaint)
            lensPaint.color = Color.rgb(21, 101, 192)
            canvas.drawCircle(iconRect.centerX(), iconRect.centerY(), iconSize * 0.15f, lensPaint)

            canvas.drawText(
                label,
                iconRect.right + baseUnit * 0.010f,
                iconRect.centerY() + badgeTextPaint.textSize * 0.35f,
                badgeTextPaint
            )
        }

        // 2. Draw Right Info Card
        val infoCardRect = RectF(infoCardLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoCardRect, cardRadius, cardRadius, cardBgPaint)

        // 3. Draw Left Separate Mini-Map Card (matching Image 1)
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(outerMargin, cardTop, outerMargin + mapCardWidth, cardBottom)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(
                miniMapBitmap,
                Rect(0, 0, miniMapBitmap.width, miniMapBitmap.height),
                mapRect,
                null
            )
            canvas.restoreToCount(saveCount)
        }

        // 4. Draw Text Lines Inside Right Info Card
        val textX = infoCardLeft + innerPadX
        var cursorY = cardTop + innerPadY

        canvas.save()
        canvas.translate(textX, cursorY)
        headlineLayout.draw(canvas)
        canvas.restore()

        // Draw crisp Country Flag (🇮🇩 Red & White) right after the last line of headline
        if (location.showCountryFlag && headlineLayout.lineCount > 0) {
            val lastLineIdx = headlineLayout.lineCount - 1
            val lastLineWidth = headlineLayout.getLineWidth(lastLineIdx)
            val lastLineTop = cursorY + headlineLayout.getLineTop(lastLineIdx)
            val lastLineBottom = cursorY + headlineLayout.getLineBottom(lastLineIdx)
            val flagW = titlePaint.textSize * 0.92f
            val flagH = titlePaint.textSize * 0.64f
            val flagLeft = (textX + lastLineWidth + baseUnit * 0.012f)
                .coerceAtMost(infoCardRight - innerPadX - flagW)
            val flagTop = lastLineTop + (lastLineBottom - lastLineTop - flagH) * 0.52f
            drawIndonesianFlag(canvas, flagLeft, flagTop, flagW, flagH)
        }

        cursorY += headlineLayout.height + lineGap * 1.2f

        // Plus Code + Address block
        canvas.save()
        canvas.translate(textX, cursorY)
        addressLayout.draw(canvas)
        canvas.restore()

        cursorY += addressLayout.height + lineGap + bodyPaint.textSize * 0.92f
        canvas.drawText(latLongLine, textX, cursorY, bodyPaint)

        cursorY += bodyLineHeight
        canvas.drawText(dateTimeLine, textX, cursorY, bodyPaint)

        if (noteLine.isNotEmpty()) {
            cursorY += bodyLineHeight
            canvas.drawText(noteLine, textX, cursorY, bodyPaint)
        }
    }

    /**
     * 100% Pixel-Accurate Renderer for Reference Image 2 (Street Map + Telemetry Sensor Icons):
     * - Separate Left Rounded Card: Light Street Map with large red pin & multicolored "Google" logo
     * - Separate Right Rounded Card:
     *   - 2-line Address Header
     *   - Row 1 (Crosshair icon): "55,7399°, 37,6016°"
     *   - Row 2 (Clock icon): "Jum, Okt 30, 2026, 13:53, GMT +7:00"
     *   - Row 3 (Elevation icon): "44m di atas permukaan laut"
     *   - Row 4 (3 sensor columns): "⌖ ±2m akurasi"   "((•)) 45,72 µT"   "🧭 12 N"
     */
    private fun drawReferenceTemplate2Telemetry(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeConfig: TimeConfig,
        location: LocationMetadata,
        styleConfig: StampStyleConfig,
        miniMapBitmap: Bitmap?
    ) {
        val baseUnit = min(width, height).toFloat()
        val outerMargin = baseUnit * 0.025f
        val gapBetweenCards = baseUnit * 0.020f
        val cardRadius = baseUnit * 0.032f
        val innerPadX = baseUnit * 0.024f
        val innerPadY = baseUnit * 0.022f

        val totalAvailableWidth = width - outerMargin * 2f
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null
        val mapCardWidth = if (showMap) totalAvailableWidth * 0.235f else 0f
        val infoCardLeft = if (showMap) outerMargin + mapCardWidth + gapBetweenCards else outerMargin
        val infoCardRight = width - outerMargin
        val infoCardWidth = infoCardRight - infoCardLeft
        val textMaxWidth = (infoCardWidth - innerPadX * 2f).toInt().coerceAtLeast(180)

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseUnit * 0.032f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseUnit * 0.0245f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val headerLayout = createStaticLayout(
            location.template2AddressHeader,
            headerPaint,
            textMaxWidth,
            maxLines = 2
        )

        val rowStep = rowPaint.textSize * 1.56f
        val contentHeight = innerPadY * 2f + headerLayout.height + baseUnit * 0.014f + rowStep * 4f
        val cardHeight = contentHeight.coerceAtLeast(if (showMap) mapCardWidth * 1.18f else contentHeight)

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            height - outerMargin - cardHeight
        } else {
            outerMargin
        }
        val cardBottom = cardTop + cardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 28, 30, 34)
            style = Paint.Style.FILL
        }

        // 1. Draw Left Light Street Map Card
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(outerMargin, cardTop, outerMargin + mapCardWidth, cardBottom)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(
                miniMapBitmap,
                Rect(0, 0, miniMapBitmap.width, miniMapBitmap.height),
                mapRect,
                null
            )
            canvas.restoreToCount(saveCount)
        }

        // 2. Draw Right Dark Translucent Telemetry Card
        val infoRect = RectF(infoCardLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoRect, cardRadius, cardRadius, cardBgPaint)

        // 3. Draw Header Address (2 lines)
        val startX = infoCardLeft + innerPadX
        var cursorY = cardTop + innerPadY
        canvas.save()
        canvas.translate(startX, cursorY)
        headerLayout.draw(canvas)
        canvas.restore()

        val iconRadius = rowPaint.textSize * 0.42f
        val textOffsetAfterIcon = iconRadius * 2.8f
        var rowBaselineY = cursorY + headerLayout.height + baseUnit * 0.014f + rowPaint.textSize * 0.90f

        // Row 1: Crosshair Icon + Comma Coordinates ("55,7399°, 37,6016°")
        val iconCenterX = startX + iconRadius
        drawCrosshairTargetIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(location.template2CommaCoordText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 2: Clock Icon + Timestamp ("Jum, Okt 30, 2026, 13:53, GMT +7:00")
        rowBaselineY += rowStep
        drawClockVectorIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(timeConfig.formatTemplate2Timestamp(), startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 3: Elevation Icon + Altitude ("44m di atas permukaan laut")
        rowBaselineY += rowStep
        drawElevationPeakIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(location.template2AltitudeText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 4: 3 Sensor Columns ("±2m akurasi" | "45,72 µT" | "12 N")
        rowBaselineY += rowStep
        val iconCenterY = rowBaselineY - rowPaint.textSize * 0.34f

        // Column 1: Accuracy
        drawCrosshairTargetIcon(canvas, iconCenterX, iconCenterY, iconRadius)
        canvas.drawText(location.template2AccuracyText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Column 2: Magnetic Field µT
        val col2X = startX + (infoCardWidth - innerPadX * 2f) * 0.44f
        drawMagneticWaveIcon(canvas, col2X + iconRadius, iconCenterY, iconRadius)
        canvas.drawText(location.template2MagneticText, col2X + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Column 3: Compass Heading ("12 N")
        val col3X = startX + (infoCardWidth - innerPadX * 2f) * 0.77f
        drawCompassVectorIcon(canvas, col3X + iconRadius, iconCenterY, iconRadius)
        canvas.drawText(location.template2CompassText, col3X + textOffsetAfterIcon, rowBaselineY, rowPaint)
    }

    private fun drawMinimalistStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeConfig: TimeConfig,
        location: LocationMetadata,
        styleConfig: StampStyleConfig,
        miniMapBitmap: Bitmap?
    ) {
        val baseUnit = min(width, height).toFloat()
        val margin = baseUnit * 0.030f
        val pad = baseUnit * 0.022f
        val cornerRadius = baseUnit * 0.020f

        val mapSize = if (styleConfig.showMiniMap && miniMapBitmap != null) {
            (baseUnit * 0.16f).roundToInt()
        } else {
            0
        }

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 193, 7)
            textSize = baseUnit * 0.032f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseUnit * 0.024f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val coordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118)
            textSize = baseUnit * 0.022f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val textLeft = margin + if (mapSize > 0) mapSize + pad * 1.2f else pad
        val textMaxWidth = (width - margin - pad - textLeft).toInt().coerceAtLeast(200)
        val addrLayout = createStaticLayout(location.fullAddress, addrPaint, textMaxWidth, maxLines = 2)

        val lineGap = baseUnit * 0.008f
        val cardHeight = (pad * 2f + timePaint.textSize + lineGap + addrLayout.height + lineGap + coordPaint.textSize)
            .coerceAtLeast(mapSize + pad * 2f)

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            height - margin - cardHeight
        } else {
            margin
        }
        val cardRect = RectF(margin, cardTop, width - margin, cardTop + cardHeight)

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.35f, 0.96f) * 255).roundToInt()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 18, 22, 28)
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        if (mapSize > 0 && miniMapBitmap != null) {
            val mapRect = RectF(
                margin + pad,
                cardTop + (cardHeight - mapSize) / 2f,
                margin + pad + mapSize,
                cardTop + (cardHeight + mapSize) / 2f
            )
            canvas.drawBitmap(
                miniMapBitmap,
                Rect(0, 0, miniMapBitmap.width, miniMapBitmap.height),
                mapRect,
                null
            )
        }

        var y = cardTop + pad + timePaint.textSize * 0.86f
        canvas.drawText(timeConfig.formatTemplate1Timestamp(), textLeft, y, timePaint)

        y += lineGap
        canvas.save()
        canvas.translate(textLeft, y)
        addrLayout.draw(canvas)
        canvas.restore()

        y += addrLayout.height + coordPaint.textSize + lineGap * 0.6f
        val compactMeta = "${location.template1LatLongText} • ${location.template2MagneticText} • ${location.template2CompassText}"
        canvas.drawText(compactMeta, textLeft, y, coordPaint)
    }

    private fun drawIndonesianFlag(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val halfH = height / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // Top half Red
        paint.color = Color.rgb(229, 37, 42)
        canvas.drawRect(left, top, left + width, top + halfH, paint)
        // Bottom half White
        paint.color = Color.WHITE
        canvas.drawRect(left, top + halfH, left + width, top + height, paint)
        // Subtle border
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(90, 0, 0, 0)
        paint.strokeWidth = 1.5f
        canvas.drawRect(left, top, left + width, top + height, paint)
    }

    private fun drawCrosshairTargetIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (r * 0.22f).coerceAtLeast(1.8f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.72f, stroke)
        canvas.drawCircle(cx, cy, r * 0.24f, fill)
        // 4 ticks
        canvas.drawLine(cx, cy - r, cx, cy - r * 0.72f, stroke)
        canvas.drawLine(cx, cy + r * 0.72f, cx, cy + r, stroke)
        canvas.drawLine(cx - r, cy, cx - r * 0.72f, cy, stroke)
        canvas.drawLine(cx + r * 0.72f, cy, cx + r, cy, stroke)
    }

    private fun drawClockVectorIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (r * 0.22f).coerceAtLeast(1.8f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(cx, cy, r * 0.85f, stroke)
        canvas.drawLine(cx, cy, cx, cy - r * 0.50f, stroke)
        canvas.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.20f, stroke)
    }

    private fun drawElevationPeakIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Top dot + mountain/pedestal base matching Image 2
        canvas.drawCircle(cx, cy - r * 0.48f, r * 0.30f, fill)
        val basePath = Path().apply {
            moveTo(cx - r * 0.85f, cy + r * 0.85f)
            quadTo(cx, cy - r * 0.05f, cx + r * 0.85f, cy + r * 0.85f)
            close()
        }
        canvas.drawPath(basePath, fill)
    }

    private fun drawMagneticWaveIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (r * 0.22f).coerceAtLeast(1.8f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(cx, cy + r * 0.35f, r * 0.32f, fill)
        val arcRect = RectF(cx - r * 0.85f, cy - r * 0.65f, cx + r * 0.85f, cy + r * 0.95f)
        canvas.drawArc(arcRect, 215f, 110f, false, stroke)
    }

    private fun drawCompassVectorIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.88f, fill)
        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 30, 34)
            style = Paint.Style.FILL
        }
        val diamond = Path().apply {
            moveTo(cx + r * 0.38f, cy - r * 0.38f)
            lineTo(cx + r * 0.12f, cy + r * 0.18f)
            lineTo(cx - r * 0.38f, cy + r * 0.38f)
            lineTo(cx - r * 0.12f, cy - r * 0.18f)
            close()
        }
        canvas.drawPath(diamond, cutoutPaint)
    }

    private fun createStaticLayout(
        text: String,
        textPaint: TextPaint,
        maxWidth: Int,
        maxLines: Int = 3
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()
    }

    fun createSampleFieldPhotoBitmap(width: Int = 1200, height: Int = 900): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sky gradient
        paint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.56f,
            Color.rgb(30, 100, 180),
            Color.rgb(135, 195, 245),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.56f, paint)

        // Distant hills
        paint.shader = null
        paint.color = Color.rgb(46, 94, 78)
        val hillPath = Path().apply {
            moveTo(0f, height * 0.56f)
            quadTo(width * 0.25f, height * 0.42f, width * 0.55f, height * 0.52f)
            quadTo(width * 0.80f, height * 0.40f, width.toFloat(), height * 0.50f)
            lineTo(width.toFloat(), height * 0.56f)
            close()
        }
        canvas.drawPath(hillPath, paint)

        // Ground & site terrain
        paint.shader = LinearGradient(
            0f, height * 0.54f, 0f, height.toFloat(),
            Color.rgb(64, 114, 72),
            Color.rgb(38, 68, 44),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.54f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        // Modern perspective road / inspection site
        val roadPath = Path().apply {
            moveTo(width * 0.42f, height * 0.54f)
            lineTo(width * 0.58f, height * 0.54f)
            lineTo(width * 0.92f, height.toFloat())
            lineTo(width * 0.08f, height.toFloat())
            close()
        }
        paint.color = Color.rgb(52, 58, 66)
        canvas.drawPath(roadPath, paint)

        // Center road markings
        paint.color = Color.rgb(255, 193, 7)
        paint.strokeWidth = width * 0.008f
        canvas.drawLine(width * 0.50f, height * 0.56f, width * 0.50f, height * 0.96f, paint)

        // Building structure on right
        paint.color = Color.rgb(205, 214, 224)
        canvas.drawRect(width * 0.64f, height * 0.28f, width * 0.86f, height * 0.55f, paint)
        paint.color = Color.rgb(56, 82, 112)
        for (row in 0..3) {
            for (col in 0..2) {
                val wx = width * (0.66f + col * 0.065f)
                val wy = height * (0.31f + row * 0.055f)
                canvas.drawRect(wx, wy, wx + width * 0.045f, wy + height * 0.035f, paint)
            }
        }

        return bmp
    }
}
