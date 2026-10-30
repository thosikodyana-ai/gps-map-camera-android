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
     * 100% Compact & Low-Profile Renderer for Reference Image 1 (Brebes Satellite Style):
     * 1. Footprint & Tinggi Kartu (Low-Profile Strip):
     *    - cardHeight locked to 12% - 15% of photoHeight (~photoHeight * 0.13f).
     *    - Bottom/Top margin from photo edge: photoHeight * 0.015f.
     *    - Left/Right margin from photo edge: photoWidth * 0.02f.
     * 2. Ukuran Mini-Map:
     *    - mapCardWidth == cardHeight (1:1 square aspect ratio), ~13% of photoHeight (~20%-22% of total stamp width).
     * 3. Skala Font Ringkas & Padat:
     *    - Title: infoCardWidth * 0.035f (Bold/Condensed)
     *    - Address + Plus Code: infoCardWidth * 0.024f (Regular)
     *    - Lat/Long, Timestamp & Note: infoCardWidth * 0.022f
     *    - Tight line spacing (~1.1x font size)
     * 4. Padding Kartu Kanan:
     *    - Top/Bottom/Left/Right padding: infoCardWidth * 0.02f.
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

        // 1. Margins & Low-Profile Footprint
        val marginX = photoWidth * 0.02f
        val marginY = photoHeight * 0.015f
        val gapBetweenCards = photoWidth * 0.012f
        val totalStampWidth = photoWidth - (marginX * 2f)
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null

        // Target cardHeight ~13% of photoHeight (clamped 12%..15%), and ~20%..22% of totalStampWidth
        val targetCardHeight = (photoHeight * 0.13f).coerceIn(photoHeight * 0.12f, photoHeight * 0.15f)
        val initialMapSize = if (showMap) {
            targetCardHeight.coerceIn(totalStampWidth * 0.19f, totalStampWidth * 0.225f)
        } else {
            0f
        }

        val infoCardLeft = if (showMap) marginX + initialMapSize + gapBetweenCards else marginX
        val infoCardRight = photoWidth - marginX
        val infoCardWidth = (infoCardRight - infoCardLeft).coerceAtLeast(photoWidth * 0.65f)

        // 3 & 4. Compact Typography & Padding (scaled from infoCardWidth)
        var innerPad = infoCardWidth * 0.02f
        var titleFontSize = infoCardWidth * 0.035f
        var addressFontSize = infoCardWidth * 0.024f
        var metaFontSize = infoCardWidth * 0.022f
        var lineGap = infoCardWidth * 0.004f

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
        val metaLineCount = if (noteLine.isNotEmpty()) 3 else 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleFontSize
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 249)
            textSize = addressFontSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 246, 249)
            textSize = metaFontSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        var textMaxWidth = (infoCardWidth - innerPad * 2f).toInt().coerceAtLeast(160)
        var headlineLayout = createCompactStaticLayout(location.template1Headline, titlePaint, textMaxWidth, maxLines = 2)
        var addressLayout = createCompactStaticLayout(location.template1FullAddressWithPlusCode, addressPaint, textMaxWidth, maxLines = 2)
        var metaLineHeight = metaPaint.textSize * 1.12f

        var rawContentHeight = innerPad * 2f +
            headlineLayout.height + lineGap +
            addressLayout.height + lineGap +
            (metaLineHeight * metaLineCount)

        // Strict height lock: never exceed 15% of photoHeight (or 22.5% of totalStampWidth)
        val maxAllowedHeight = (photoHeight * 0.15f).coerceAtMost(totalStampWidth * 0.23f)
        val minAllowedHeight = (photoHeight * 0.12f).coerceAtMost(maxAllowedHeight)

        if (rawContentHeight > maxAllowedHeight) {
            val scale = (maxAllowedHeight / rawContentHeight).coerceIn(0.60f, 1.0f)
            innerPad *= scale
            titleFontSize *= scale
            addressFontSize *= scale
            metaFontSize *= scale
            lineGap *= scale

            titlePaint.textSize = titleFontSize
            addressPaint.textSize = addressFontSize
            metaPaint.textSize = metaFontSize
            metaLineHeight = metaPaint.textSize * 1.12f

            textMaxWidth = (infoCardWidth - innerPad * 2f).toInt().coerceAtLeast(160)
            headlineLayout = createCompactStaticLayout(location.template1Headline, titlePaint, textMaxWidth, maxLines = 2)
            addressLayout = createCompactStaticLayout(location.template1FullAddressWithPlusCode, addressPaint, textMaxWidth, maxLines = 2)

            rawContentHeight = innerPad * 2f +
                headlineLayout.height + lineGap +
                addressLayout.height + lineGap +
                (metaLineHeight * metaLineCount)
        }

        // 2. Lock cardHeight and 1:1 Square mapCardWidth
        val cardHeight = rawContentHeight.coerceIn(minAllowedHeight, maxAllowedHeight)
        val mapCardWidth = if (showMap) cardHeight else 0f
        val finalInfoLeft = if (showMap) marginX + mapCardWidth + gapBetweenCards else marginX
        val finalInfoWidth = infoCardRight - finalInfoLeft
        val cardRadius = cardHeight * 0.085f

        // Re-verify layouts with finalInfoWidth
        val finalTextMaxW = (finalInfoWidth - innerPad * 2f).toInt().coerceAtLeast(160)
        headlineLayout = createCompactStaticLayout(location.template1Headline, titlePaint, finalTextMaxW, maxLines = 2)
        addressLayout = createCompactStaticLayout(location.template1FullAddressWithPlusCode, addressPaint, finalTextMaxW, maxLines = 2)

        val badgeTabHeight = if (styleConfig.showBrandBadgeTab) cardHeight * 0.21f else 0f

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            photoHeight - marginY - cardHeight
        } else {
            marginY + badgeTabHeight
        }
        val cardBottom = cardTop + cardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 42, 44, 48)
            style = Paint.Style.FILL
        }

        // Draw Top-Right Attached Tab Badge "[📷] GPS Map Camera"
        if (styleConfig.showBrandBadgeTab) {
            val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = metaPaint.textSize * 0.92f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val label = "GPS Map Camera"
            val iconSize = badgeTabHeight * 0.60f
            val tabPadX = finalInfoWidth * 0.015f
            val tabWidth = tabPadX * 2f + iconSize + finalInfoWidth * 0.010f + badgeTextPaint.measureText(label)
            val tabLeft = (infoCardRight - tabWidth).coerceAtLeast(finalInfoLeft)
            val tabTop = cardTop - badgeTabHeight + cardRadius * 0.25f
            val tabRect = RectF(tabLeft, tabTop, infoCardRight, cardTop + cardRadius)

            canvas.drawRoundRect(tabRect, cardRadius * 0.70f, cardRadius * 0.70f, cardBgPaint)

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
                iconRect.right + finalInfoWidth * 0.010f,
                iconRect.centerY() + badgeTextPaint.textSize * 0.35f,
                badgeTextPaint
            )
        }

        // Draw Right Info Card
        val infoCardRect = RectF(finalInfoLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoCardRect, cardRadius, cardRadius, cardBgPaint)

        // Draw Left 1:1 Square Mini-Map Card with Center-Crop
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(marginX, cardTop, marginX + mapCardWidth, cardBottom)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            drawBitmapCenterCrop(canvas, miniMapBitmap, mapRect)
            canvas.restoreToCount(saveCount)
        }

        // Vertically center the compact text block inside cardHeight so top/bottom padding is balanced
        val actualBlockHeight = headlineLayout.height + lineGap +
            addressLayout.height + lineGap +
            (metaLineHeight * metaLineCount)
        val textX = finalInfoLeft + innerPad
        var cursorY = cardTop + ((cardHeight - actualBlockHeight) / 2f).coerceAtLeast(innerPad * 0.75f)

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
            val flagLeft = (textX + lastLineWidth + finalInfoWidth * 0.012f)
                .coerceAtMost(infoCardRight - innerPad - flagW)
            val flagTop = lastLineTop + (lastLineBottom - lastLineTop - flagH) * 0.52f
            drawIndonesianFlag(canvas, flagLeft, flagTop, flagW, flagH)
        }

        cursorY += headlineLayout.height + lineGap

        // Plus Code + Address block
        canvas.save()
        canvas.translate(textX, cursorY)
        addressLayout.draw(canvas)
        canvas.restore()

        cursorY += addressLayout.height + lineGap + metaPaint.textSize * 0.90f
        canvas.drawText(latLongLine, textX, cursorY, metaPaint)

        cursorY += metaLineHeight
        canvas.drawText(dateTimeLine, textX, cursorY, metaPaint)

        if (noteLine.isNotEmpty()) {
            cursorY += metaLineHeight
            canvas.drawText(noteLine, textX, cursorY, metaPaint)
        }
    }

    /**
     * 100% Compact & Low-Profile Renderer for Reference Image 2 (Street Map + Telemetry Sensor Icons):
     * - Locked 12% - 15% photoHeight strip (~13% photoHeight)
     * - 1:1 Square Mini-Map (~20%-22% of total stamp width)
     * - Compact font scaling (0.035f header, 0.022f rows) & 0.02f padding
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

        val marginX = photoWidth * 0.02f
        val marginY = photoHeight * 0.015f
        val gapBetweenCards = photoWidth * 0.012f
        val totalStampWidth = photoWidth - (marginX * 2f)
        val showMap = styleConfig.showMiniMap && miniMapBitmap != null

        val targetCardHeight = (photoHeight * 0.13f).coerceIn(photoHeight * 0.12f, photoHeight * 0.15f)
        val initialMapSize = if (showMap) {
            targetCardHeight.coerceIn(totalStampWidth * 0.19f, totalStampWidth * 0.225f)
        } else {
            0f
        }

        val infoCardLeft = if (showMap) marginX + initialMapSize + gapBetweenCards else marginX
        val infoCardRight = photoWidth - marginX
        val infoCardWidth = (infoCardRight - infoCardLeft).coerceAtLeast(photoWidth * 0.65f)

        var innerPad = infoCardWidth * 0.02f
        var headerFontSize = infoCardWidth * 0.034f
        var rowFontSize = infoCardWidth * 0.022f
        var headerGap = infoCardWidth * 0.006f

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = headerFontSize
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = rowFontSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        var textMaxWidth = (infoCardWidth - innerPad * 2f).toInt().coerceAtLeast(160)
        var headerLayout = createCompactStaticLayout(
            location.template2AddressHeader,
            headerPaint,
            textMaxWidth,
            maxLines = 2
        )
        var rowStep = rowPaint.textSize * 1.18f
        var rawContentHeight = innerPad * 2f + headerLayout.height + headerGap + (rowStep * 4f)

        val maxAllowedHeight = (photoHeight * 0.15f).coerceAtMost(totalStampWidth * 0.23f)
        val minAllowedHeight = (photoHeight * 0.12f).coerceAtMost(maxAllowedHeight)

        if (rawContentHeight > maxAllowedHeight) {
            val scale = (maxAllowedHeight / rawContentHeight).coerceIn(0.60f, 1.0f)
            innerPad *= scale
            headerFontSize *= scale
            rowFontSize *= scale
            headerGap *= scale

            headerPaint.textSize = headerFontSize
            rowPaint.textSize = rowFontSize
            rowStep = rowPaint.textSize * 1.18f

            textMaxWidth = (infoCardWidth - innerPad * 2f).toInt().coerceAtLeast(160)
            headerLayout = createCompactStaticLayout(
                location.template2AddressHeader,
                headerPaint,
                textMaxWidth,
                maxLines = 2
            )
            rawContentHeight = innerPad * 2f + headerLayout.height + headerGap + (rowStep * 4f)
        }

        val cardHeight = rawContentHeight.coerceIn(minAllowedHeight, maxAllowedHeight)
        val mapCardWidth = if (showMap) cardHeight else 0f
        val finalInfoLeft = if (showMap) marginX + mapCardWidth + gapBetweenCards else marginX
        val finalInfoWidth = infoCardRight - finalInfoLeft
        val cardRadius = cardHeight * 0.10f

        val finalTextMaxW = (finalInfoWidth - innerPad * 2f).toInt().coerceAtLeast(160)
        headerLayout = createCompactStaticLayout(
            location.template2AddressHeader,
            headerPaint,
            finalTextMaxW,
            maxLines = 2
        )

        val cardTop = if (styleConfig.position == StampPosition.BOTTOM) {
            photoHeight - marginY - cardHeight
        } else {
            marginY
        }
        val cardBottom = cardTop + cardHeight

        val bgAlpha = (styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f) * 255).roundToInt()
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(bgAlpha, 28, 30, 34)
            style = Paint.Style.FILL
        }

        // 1. Draw Left 1:1 Square Light Street Map Card with Center-Crop
        if (showMap && miniMapBitmap != null) {
            val mapRect = RectF(marginX, cardTop, marginX + mapCardWidth, cardBottom)
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(mapRect, cardRadius, cardRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            drawBitmapCenterCrop(canvas, miniMapBitmap, mapRect)
            canvas.restoreToCount(saveCount)
        }

        // 2. Draw Right Dark Translucent Telemetry Card
        val infoRect = RectF(finalInfoLeft, cardTop, infoCardRight, cardBottom)
        canvas.drawRoundRect(infoRect, cardRadius, cardRadius, cardBgPaint)

        // 3. Draw Compact Content Inside Right Card
        val actualBlockHeight = headerLayout.height + headerGap + (rowStep * 4f)
        val startX = finalInfoLeft + innerPad
        val cursorY = cardTop + ((cardHeight - actualBlockHeight) / 2f).coerceAtLeast(innerPad * 0.75f)

        canvas.save()
        canvas.translate(startX, cursorY)
        headerLayout.draw(canvas)
        canvas.restore()

        val iconRadius = rowPaint.textSize * 0.40f
        val textOffsetAfterIcon = iconRadius * 2.6f
        var rowBaselineY = cursorY + headerLayout.height + headerGap + rowPaint.textSize * 0.88f

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
        rowBaselineY += rowStep
        drawClockVectorIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(timeConfig.formatTemplate2Timestamp(), startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 3: Elevation Icon + Altitude
        rowBaselineY += rowStep
        drawElevationPeakIcon(
            canvas = canvas,
            cx = iconCenterX,
            cy = rowBaselineY - rowPaint.textSize * 0.34f,
            r = iconRadius
        )
        canvas.drawText(location.template2AltitudeText, startX + textOffsetAfterIcon, rowBaselineY, rowPaint)

        // Row 4: 3 Sensor Columns ("±m akurasi" | "µT" | "N")
        rowBaselineY += rowStep
        val iconCenterY = rowBaselineY - rowPaint.textSize * 0.34f
        val usableInnerW = finalInfoWidth - innerPad * 2f

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
        val marginX = photoWidth * 0.02f
        val marginY = photoHeight * 0.015f
        val totalW = photoWidth - marginX * 2f
        val pad = totalW * 0.018f
        val cornerRadius = totalW * 0.016f

        val mapSize = if (styleConfig.showMiniMap && miniMapBitmap != null) {
            (photoHeight * 0.09f).roundToInt()
        } else {
            0
        }

        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 193, 7)
            textSize = totalW * 0.024f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val addrPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = totalW * 0.021f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val coordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118)
            textSize = totalW * 0.019f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val textLeft = marginX + if (mapSize > 0) mapSize + pad * 1.2f else pad
        val textMaxWidth = (photoWidth - marginX - pad - textLeft).toInt().coerceAtLeast(180)
        val addrLayout = createCompactStaticLayout(location.fullAddress, addrPaint, textMaxWidth, maxLines = 2)

        val lineGap = totalW * 0.005f
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

    /**
     * Compact StaticLayout with ~1.04x line spacing multiplier (~1.1x font size)
     * so 4-5 lines fit snugly inside the low-profile card height.
     */
    private fun createCompactStaticLayout(
        text: String,
        textPaint: TextPaint,
        maxWidth: Int,
        maxLines: Int = 2
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.04f)
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
