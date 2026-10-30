package com.example.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import kotlin.math.roundToInt

object BitmapStampRenderer {

    /**
     * Rotates the raw photo bitmap FIRST by [rotationDegrees] (0, 90, 180, 270)
     * so that the photo dimensions (newWidth, newHeight) are updated before drawing the stamp.
     */
    fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply {
            postRotate(normalized.toFloat())
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Pipeline Stempel:
     * 1. Rotasi diaplikasikan ke Bitmap FOTO TERLEBIH DAHULU ([rotationDegrees]).
     * 2. Hitung ulang lebar/tinggi foto hasil rotasi (newWidth, newHeight) dan deteksi orientasi (isLandscape).
     * 3. Gambar STAMP secara horizontal di posisi bawah (atau atas) foto hasil rotasi agar teks & peta selalu tegak lurus.
     */
    fun renderStampedBitmap(
        sourceBitmap: Bitmap,
        timeConfig: TimeConfig,
        location: LocationMetadata,
        styleConfig: StampStyleConfig,
        miniMapBitmap: Bitmap?,
        rotationDegrees: Int = 0
    ): Bitmap {
        val rotatedPhoto = rotateBitmap(sourceBitmap, rotationDegrees)
        val width = rotatedPhoto.width.coerceAtLeast(480)
        val height = rotatedPhoto.height.coerceAtLeast(480)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw rotated photo first
        canvas.drawBitmap(rotatedPhoto, 0f, 0f, null)

        // 2. Render upright horizontal stamp onto the rotated photo dimensions
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
     * 1:1 Calibrated Renderer for Reference Image 1 (Brebes Satellite Style)
     * with Adaptive Portrait & Landscape Proportions:
     *
     * 1. Deteksi Orientasi Foto:
     *    val isLandscape = photoWidth > photoHeight
     * 2. Rasio Tinggi & Margin Dinamis:
     *    val cardHeight = photoHeight * (if (isLandscape) 0.32f else 0.215f)
     *    val marginLeft = photoWidth * (if (isLandscape) 0.05f else 0.035f)
     *    val marginRight = photoWidth * (if (isLandscape) 0.05f else 0.035f)
     *    val marginBottom = photoHeight * (if (isLandscape) 0.04f else 0.025f)
     *    val gap = photoWidth * (if (isLandscape) 0.012f else 0.015f)
     * 3. Kotak Mini-Map (Tetap Persegi 1:1):
     *    val mapWidth = cardHeight
     *    val mapHeight = cardHeight
     * 4. Kartu Teks Kanan:
     *    val infoCardWidth = photoWidth - (marginLeft + marginRight + mapWidth + gap)
     *    val infoCardHeight = cardHeight
     *    val innerPadding = infoCardWidth * (if (isLandscape) 0.025f else 0.035f)
     * 5. Skala Tipografi Adaptif:
     *    val fontHeadline = infoCardWidth * (if (isLandscape) 0.034f else 0.046f)
     *    val fontBody = infoCardWidth * (if (isLandscape) 0.022f else 0.030f)
     *    val fontCoordsTime = infoCardWidth * (if (isLandscape) 0.020f else 0.028f)
     *    val fontNote = infoCardWidth * (if (isLandscape) 0.018f else 0.026f)
     *    val lineSpacing = fontBody * 1.2f
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
        val photoWidth = width.toFloat()
        val photoHeight = height.toFloat()

        // 1. Deteksi Orientasi Foto
        val isLandscape = photoWidth > photoHeight
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null

        // 2. Rasio Tinggi & Margin Dinamis
        val cardHeight = photoHeight * (if (isLandscape) 0.32f else 0.215f)
        val marginLeft = photoWidth * (if (isLandscape) 0.05f else 0.035f)
        val marginRight = photoWidth * (if (isLandscape) 0.05f else 0.035f)
        val marginBottom = photoHeight * (if (isLandscape) 0.04f else 0.025f)
        val gap = photoWidth * (if (isLandscape) 0.012f else 0.015f)

        // 3. Kotak Mini-Map (Tetap Persegi 1:1)
        val mapWidth = if (showMap) cardHeight else 0f
        val mapHeight = cardHeight

        // 4. Kartu Teks Kanan
        val infoCardLeft = if (showMap) marginLeft + mapWidth + gap else marginLeft
        val infoCardRight = photoWidth - marginRight
        val infoCardWidth = (infoCardRight - infoCardLeft).coerceAtLeast(photoWidth * 0.45f)
        val infoCardHeight = cardHeight
        var innerPadding = infoCardWidth * (if (isLandscape) 0.025f else 0.035f)

        // 5. Skala Tipografi Adaptif
        var fontHeadline = infoCardWidth * (if (isLandscape) 0.034f else 0.046f)
        var fontBody = infoCardWidth * (if (isLandscape) 0.022f else 0.030f)
        var fontCoordsTime = infoCardWidth * (if (isLandscape) 0.020f else 0.028f)
        var fontNote = infoCardWidth * (if (isLandscape) 0.018f else 0.026f)

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

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontHeadline
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 249)
            textSize = fontBody
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val coordsTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 249)
            textSize = fontCoordsTime
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 249)
            textSize = fontNote
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        var textMaxWidth = (infoCardWidth - innerPadding * 2f).toInt().coerceAtLeast(160)
        var headlineLayout = createAdaptiveStaticLayout(
            text = location.template1Headline,
            textPaint = titlePaint,
            maxWidth = textMaxWidth,
            lineSpacingMultiplier = 1.08f,
            maxLines = 2
        )
        var addressLayout = createAdaptiveStaticLayout(
            text = location.template1FullAddressWithPlusCode,
            textPaint = bodyPaint,
            maxWidth = textMaxWidth,
            lineSpacingMultiplier = 1.15f,
            maxLines = 3
        )

        var coordsStep = fontCoordsTime * 1.20f
        var noteStep = if (noteLine.isNotEmpty()) fontNote * 1.20f else 0f
        var sectionGap = (fontBody * 0.22f).coerceAtLeast(2f)

        var totalTextHeight = headlineLayout.height + sectionGap +
            addressLayout.height + sectionGap +
            (coordsStep * 2f) + noteStep

        val maxInnerHeight = (infoCardHeight - innerPadding * 1.6f).coerceAtLeast(infoCardHeight * 0.72f)
        if (totalTextHeight > maxInnerHeight) {
            val fitScale = (maxInnerHeight / totalTextHeight).coerceIn(0.68f, 1.0f)
            innerPadding *= fitScale
            fontHeadline *= fitScale
            fontBody *= fitScale
            fontCoordsTime *= fitScale
            fontNote *= fitScale
            sectionGap *= fitScale

            titlePaint.textSize = fontHeadline
            bodyPaint.textSize = fontBody
            coordsTimePaint.textSize = fontCoordsTime
            notePaint.textSize = fontNote

            textMaxWidth = (infoCardWidth - innerPadding * 2f).toInt().coerceAtLeast(160)
            headlineLayout = createAdaptiveStaticLayout(
                text = location.template1Headline,
                textPaint = titlePaint,
                maxWidth = textMaxWidth,
                lineSpacingMultiplier = 1.08f,
                maxLines = 2
            )
            addressLayout = createAdaptiveStaticLayout(
                text = location.template1FullAddressWithPlusCode,
                textPaint = bodyPaint,
                maxWidth = textMaxWidth,
                lineSpacingMultiplier = 1.15f,
                maxLines = 3
            )
            coordsStep = fontCoordsTime * 1.20f
            noteStep = if (noteLine.isNotEmpty()) fontNote * 1.20f else 0f
            totalTextHeight = headlineLayout.height + sectionGap +
                addressLayout.height + sectionGap +
                (coordsStep * 2f) + noteStep
        }

        val cardRadius = cardHeight * 0.065f
        val badgeTabHeight = if (styleConfig.showBrandBadgeTab) cardHeight * 0.165f else 0f

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            photoHeight - marginBottom - infoCardHeight
        } else {
            marginBottom + badgeTabHeight
        }
        val cardBottom = cardTop + infoCardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 42, 44, 48)
            style = Paint.Style.FILL
        }

        // Draw Top-Right Attached Tab Badge "[📷] GPS Map Camera"
        if (styleConfig.showBrandBadgeTab) {
            val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = fontNote * 0.92f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val label = "GPS Map Camera"
            val iconSize = badgeTabHeight * 0.62f
            val tabPadX = infoCardWidth * 0.016f
            val tabWidth = tabPadX * 2f + iconSize + infoCardWidth * 0.012f + badgeTextPaint.measureText(label)
            val tabLeft = (infoCardRight - tabWidth).coerceAtLeast(infoCardLeft)
            val tabTop = cardTop - badgeTabHeight + cardRadius * 0.25f
            val tabRect = RectF(tabLeft, tabTop, infoCardRight, cardTop + cardRadius)

            canvas.drawRoundRect(tabRect, cardRadius * 0.72f, cardRadius * 0.72f, cardBgPaint)

            val iconLeft = tabLeft + tabPadX
            val iconTop = tabTop + (badgeTabHeight - iconSize) * 0.44f
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
                iconRect.right + infoCardWidth * 0.012f,
                iconRect.centerY() + badgeTextPaint.textSize * 0.35f,
                badgeTextPaint
            )
        }

        // Draw Right Info Card
        val infoCardRect = RectF(infoCardLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoCardRect, cardRadius, cardRadius, cardBgPaint)

        // Draw Left 1:1 Square Mini-Map Card with Center-Crop (mapWidth == mapHeight == cardHeight)
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(marginLeft, cardTop, marginLeft + mapWidth, cardTop + mapHeight)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            drawBitmapCenterCrop(canvas, miniMapBitmap, mapRect)
            canvas.restoreToCount(saveCount)
        }

        // Distribute vertical space cleanly inside the locked cardHeight
        val extraVerticalSpace = (infoCardHeight - innerPadding * 2f - totalTextHeight).coerceAtLeast(0f)
        val dynamicGap = sectionGap + (extraVerticalSpace * 0.25f)
        val lineStepBoost = (extraVerticalSpace * 0.50f) / 3f

        val textX = infoCardLeft + innerPadding
        var cursorY = cardTop + ((infoCardHeight - (totalTextHeight + extraVerticalSpace * 0.75f)) / 2f)
            .coerceAtLeast(innerPadding * 0.75f)

        // 1. Headline (Wilayah/Kecamatan + Provinsi + Negara)
        canvas.save()
        canvas.translate(textX, cursorY)
        headlineLayout.draw(canvas)
        canvas.restore()

        // Country Flag (🇮🇩 Red & White) right after the last line of headline
        if (location.showCountryFlag && headlineLayout.lineCount > 0) {
            val lastLineIdx = headlineLayout.lineCount - 1
            val lastLineWidth = headlineLayout.getLineWidth(lastLineIdx)
            val lastLineTop = cursorY + headlineLayout.getLineTop(lastLineIdx)
            val lastLineBottom = cursorY + headlineLayout.getLineBottom(lastLineIdx)
            val flagW = titlePaint.textSize * 0.88f
            val flagH = titlePaint.textSize * 0.60f
            val flagLeft = (textX + lastLineWidth + infoCardWidth * 0.014f)
                .coerceAtMost(infoCardRight - innerPadding - flagW)
            val flagTop = lastLineTop + (lastLineBottom - lastLineTop - flagH) * 0.52f
            drawIndonesianFlag(canvas, flagLeft, flagTop, flagW, flagH)
        }

        cursorY += headlineLayout.height + dynamicGap

        // 2. Plus Code + Full Address block (line-height ~1.2x)
        canvas.save()
        canvas.translate(textX, cursorY)
        addressLayout.draw(canvas)
        canvas.restore()

        // 3. Coordinates & Timestamp
        cursorY += addressLayout.height + dynamicGap + coordsTimePaint.textSize * 0.90f
        canvas.drawText(latLongLine, textX, cursorY, coordsTimePaint)

        cursorY += coordsStep + lineStepBoost
        canvas.drawText(dateTimeLine, textX, cursorY, coordsTimePaint)

        // 4. Note
        if (noteLine.isNotEmpty()) {
            cursorY += noteStep + lineStepBoost
            canvas.drawText(noteLine, textX, cursorY, notePaint)
        }
    }

    /**
     * 1:1 Calibrated Renderer for Reference Image 2 (Street Map + Telemetry Sensor Icons)
     * using the exact same Adaptive Portrait & Landscape Proportions:
     * - cardHeight = photoHeight * (if (isLandscape) 0.32f else 0.215f)
     * - 1:1 Square Mini-Map (mapWidth = cardHeight, mapHeight = cardHeight)
     * - Adaptive margins, innerPadding, and typography scale
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
        val photoWidth = width.toFloat()
        val photoHeight = height.toFloat()

        val isLandscape = photoWidth > photoHeight
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null

        val cardHeight = photoHeight * (if (isLandscape) 0.32f else 0.215f)
        val marginLeft = photoWidth * (if (isLandscape) 0.05f else 0.035f)
        val marginRight = photoWidth * (if (isLandscape) 0.05f else 0.035f)
        val marginBottom = photoHeight * (if (isLandscape) 0.04f else 0.025f)
        val gap = photoWidth * (if (isLandscape) 0.012f else 0.015f)

        val mapWidth = if (showMap) cardHeight else 0f
        val mapHeight = cardHeight

        val infoCardLeft = if (showMap) marginLeft + mapWidth + gap else marginLeft
        val infoCardRight = photoWidth - marginRight
        val infoCardWidth = (infoCardRight - infoCardLeft).coerceAtLeast(photoWidth * 0.45f)
        val infoCardHeight = cardHeight
        var innerPadding = infoCardWidth * (if (isLandscape) 0.025f else 0.035f)

        var fontHeadline = infoCardWidth * (if (isLandscape) 0.032f else 0.042f)
        var fontRow = infoCardWidth * (if (isLandscape) 0.020f else 0.028f)
        var headerGap = fontRow * 0.35f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontHeadline
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontRow
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        var textMaxWidth = (infoCardWidth - innerPadding * 2f).toInt().coerceAtLeast(160)
        var headerLayout = createAdaptiveStaticLayout(
            text = location.template2AddressHeader,
            textPaint = headerPaint,
            maxWidth = textMaxWidth,
            lineSpacingMultiplier = 1.12f,
            maxLines = 2
        )
        var rowStep = fontRow * 1.25f
        var totalTextHeight = headerLayout.height + headerGap + (rowStep * 4f)

        val maxInnerHeight = (infoCardHeight - innerPadding * 1.6f).coerceAtLeast(infoCardHeight * 0.72f)
        if (totalTextHeight > maxInnerHeight) {
            val fitScale = (maxInnerHeight / totalTextHeight).coerceIn(0.68f, 1.0f)
            innerPadding *= fitScale
            fontHeadline *= fitScale
            fontRow *= fitScale
            headerGap *= fitScale

            headerPaint.textSize = fontHeadline
            rowPaint.textSize = fontRow
            rowStep = fontRow * 1.25f

            textMaxWidth = (infoCardWidth - innerPadding * 2f).toInt().coerceAtLeast(160)
            headerLayout = createAdaptiveStaticLayout(
                text = location.template2AddressHeader,
                textPaint = headerPaint,
                maxWidth = textMaxWidth,
                lineSpacingMultiplier = 1.12f,
                maxLines = 2
            )
            totalTextHeight = headerLayout.height + headerGap + (rowStep * 4f)
        }

        val cardRadius = cardHeight * 0.075f
        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            photoHeight - marginBottom - infoCardHeight
        } else {
            marginBottom
        }
        val cardBottom = cardTop + infoCardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 28, 30, 34)
            style = Paint.Style.FILL
        }

        // 1. Draw Left 1:1 Square Light Street Map Card with Center-Crop
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(marginLeft, cardTop, marginLeft + mapWidth, cardTop + mapHeight)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            drawBitmapCenterCrop(canvas, miniMapBitmap, mapRect)
            canvas.restoreToCount(saveCount)
        }

        // 2. Draw Right Dark Translucent Telemetry Card
        val infoRect = RectF(infoCardLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoRect, cardRadius, cardRadius, cardBgPaint)

        // 3. Distribute Telemetry Content Inside Right Card
        val extraSpace = (infoCardHeight - innerPadding * 2f - totalTextHeight).coerceAtLeast(0f)
        val boostedRowStep = rowStep + (extraSpace * 0.65f) / 4f
        val boostedHeaderGap = headerGap + (extraSpace * 0.25f)

        val startX = infoCardLeft + innerPadding
        val cursorY = cardTop + ((infoCardHeight - (headerLayout.height + boostedHeaderGap + boostedRowStep * 4f)) / 2f)
            .coerceAtLeast(innerPadding * 0.75f)

        canvas.save()
        canvas.translate(startX, cursorY)
        headerLayout.draw(canvas)
        canvas.restore()

        val iconRadius = rowPaint.textSize * 0.40f
        val textOffsetAfterIcon = iconRadius * 2.6f
        var rowBaselineY = cursorY + headerLayout.height + boostedHeaderGap + rowPaint.textSize * 0.88f

        // Row 1: Crosshair Icon + Comma Coordinates
        val iconCenterX = startX + iconRadius
        drawCrosshairTargetIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(location.template2CommaCoordText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 2: Clock Icon + Timestamp
        rowBaselineY += boostedRowStep
        drawClockVectorIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(timeConfig.formatTemplate2Timestamp(), startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 3: Elevation Icon + Altitude
        rowBaselineY += boostedRowStep
        drawElevationPeakIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(location.template2AltitudeText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 4: 3 Sensor Columns ("±m akurasi" | "µT" | "N")
        rowBaselineY += boostedRowStep
        val iconCenterY = rowBaselineY - rowPaint.textSize * 0.34f
        val usableInnerW = infoCardWidth - innerPadding * 2f

        drawCrosshairTargetIcon(canvas, iconCenterX, iconCenterY, iconRadius)
        canvas.drawText(location.template2AccuracyText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        val col2X = startX + usableInnerW * 0.44f
        drawMagneticWaveIcon(canvas, col2X + iconRadius, iconCenterY, iconRadius)
        canvas.drawText(location.template2MagneticText, col2X + textOffsetAfterIcon, rowBaselineY, rowPaint)

        val col3X = startX + usableInnerW * 0.77f
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
        val photoWidth = width.toFloat()
        val photoHeight = height.toFloat()
        val isLandscape = photoWidth > photoHeight
        val marginX = photoWidth * (if (isLandscape) 0.05f else 0.035f)
        val marginY = photoHeight * (if (isLandscape) 0.04f else 0.025f)
        val totalW = photoWidth - marginX * 2f
        val pad = totalW * 0.022f
        val cornerRadius = totalW * 0.018f

        val mapSize = if (styleConfig.showMiniMap && miniMapBitmap != null) {
            (photoHeight * (if (isLandscape) 0.16f else 0.11f)).roundToInt()
        } else {
            0
        }

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 193, 7)
            textSize = totalW * (if (isLandscape) 0.022f else 0.028f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = totalW * (if (isLandscape) 0.019f else 0.024f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val coordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118)
            textSize = totalW * (if (isLandscape) 0.017f else 0.022f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val textLeft = marginX + if (mapSize > 0) mapSize + pad * 1.2f else pad
        val textMaxWidth = (photoWidth - marginX - pad - textLeft).toInt().coerceAtLeast(180)
        val addrLayout = createAdaptiveStaticLayout(location.fullAddress, addrPaint, textMaxWidth, 1.15f, maxLines = 2)

        val lineGap = totalW * 0.006f
        val cardHeight = (pad * 2f + timePaint.textSize + lineGap + addrLayout.height + lineGap + coordPaint.textSize)
            .coerceAtLeast(mapSize + pad * 1.6f)

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            photoHeight - marginY - cardHeight
        } else {
            marginY
        }
        val cardRect = RectF(marginX, cardTop, photoWidth - marginX, cardTop + cardHeight)

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.35f, 0.96f) * 255).roundToInt()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 18, 22, 28)
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        if (mapSize > 0 && miniMapBitmap != null) {
            val mapRect = RectF(
                marginX + pad * 0.8f,
                cardTop + (cardHeight - mapSize) / 2f,
                marginX + pad * 0.8f + mapSize,
                cardTop + (cardHeight + mapSize) / 2f
            )
            drawBitmapCenterCrop(canvas, miniMapBitmap, mapRect)
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

    /**
     * Draws a Bitmap into [dstRect] using Center-Crop math so the bitmap is NEVER squished or stretched.
     */
    private fun drawBitmapCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        dstRect: RectF
    ) {
        val dstW = dstRect.width().coerceAtLeast(1f)
        val dstH = dstRect.height().coerceAtLeast(1f)
        val srcW = bitmap.width.toFloat().coerceAtLeast(1f)
        val srcH = bitmap.height.toFloat().coerceAtLeast(1f)

        val dstAspect = dstW / dstH
        val srcAspect = srcW / srcH

        val srcRect = if (srcAspect > dstAspect) {
            val cropW = (srcH * dstAspect).roundToInt().coerceIn(1, bitmap.width)
            val left = ((bitmap.width - cropW) / 2).coerceAtLeast(0)
            Rect(left, 0, left + cropW, bitmap.height)
        } else {
            val cropH = (srcW / dstAspect).roundToInt().coerceIn(1, bitmap.height)
            val top = ((bitmap.height - cropH) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, top + cropH)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
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
        paint.color = Color.rgb(229, 37, 42)
        canvas.drawRect(left, top, left + width, top + halfH, paint)
        paint.color = Color.WHITE
        canvas.drawRect(left, top + halfH, left + width, top + height, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb(90, 0, 0, 0)
        paint.strokeWidth = 1.5f
        canvas.drawRect(left, top, left + width, top + height, paint)
    }

    private fun drawCrosshairTargetIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (r * 0.22f).coerceAtLeast(1.5f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 0.72f, stroke)
        canvas.drawCircle(cx, cy, r * 0.24f, fill)
        canvas.drawLine(cx, cy - r, cx, cy - r * 0.72f, stroke)
        canvas.drawLine(cx, cy + r * 0.72f, cx, cy + r, stroke)
        canvas.drawLine(cx - r, cy, cx - r * 0.72f, cy, stroke)
        canvas.drawLine(cx + r * 0.72f, cy, cx + r, cy, stroke)
    }

    private fun drawClockVectorIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (r * 0.22f).coerceAtLeast(1.5f)
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
            strokeWidth = (r * 0.22f).coerceAtLeast(1.5f)
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

    private fun createAdaptiveStaticLayout(
        text: String,
        textPaint: TextPaint,
        maxWidth: Int,
        lineSpacingMultiplier: Float = 1.15f,
        maxLines: Int = 2
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()
    }

    fun createSampleFieldPhotoBitmap(width: Int = 1200, height: Int = 1600): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.56f,
            Color.rgb(30, 100, 180),
            Color.rgb(135, 195, 245),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.56f, paint)

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

        paint.shader = LinearGradient(
            0f, height * 0.54f, 0f, height.toFloat(),
            Color.rgb(64, 114, 72),
            Color.rgb(38, 68, 44),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, height * 0.54f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val roadPath = Path().apply {
            moveTo(width * 0.42f, height * 0.54f)
            lineTo(width * 0.58f, height * 0.54f)
            lineTo(width * 0.92f, height.toFloat())
            lineTo(width * 0.08f, height.toFloat())
            close()
        }
        paint.color = Color.rgb(52, 58, 66)
        canvas.drawPath(roadPath, paint)

        paint.color = Color.rgb(255, 193, 7)
        paint.strokeWidth = width * 0.008f
        canvas.drawLine(width * 0.50f, height * 0.56f, width * 0.50f, height * 0.96f, paint)

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
