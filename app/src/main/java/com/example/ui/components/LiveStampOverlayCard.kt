package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LocationMetadata
import com.example.model.StampStyleConfig
import com.example.model.StampTemplate
import com.example.model.TimeConfig
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalAmber

@Composable
fun LiveStampOverlayCard(
    timeConfig: TimeConfig,
    location: LocationMetadata,
    styleConfig: StampStyleConfig,
    miniMapBitmap: Bitmap?,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cachedImageBitmap = remember(miniMapBitmap) {
        miniMapBitmap?.asImageBitmap()
    }

    when (styleConfig.template) {
        StampTemplate.CLASSIC -> Reference1ClassicLiveStamp(
            timeConfig = timeConfig,
            location = location,
            styleConfig = styleConfig,
            miniMapImageBitmap = cachedImageBitmap,
            onTimeClick = onTimeClick,
            onLocationClick = onLocationClick,
            modifier = modifier
        )
        StampTemplate.MODERN -> Reference2TelemetryLiveStamp(
            timeConfig = timeConfig,
            location = location,
            styleConfig = styleConfig,
            miniMapImageBitmap = cachedImageBitmap,
            onTimeClick = onTimeClick,
            onLocationClick = onLocationClick,
            modifier = modifier
        )
        StampTemplate.MINIMALIST -> MinimalistLiveStamp(
            timeConfig = timeConfig,
            location = location,
            styleConfig = styleConfig,
            miniMapImageBitmap = cachedImageBitmap,
            onTimeClick = onTimeClick,
            onLocationClick = onLocationClick,
            modifier = modifier
        )
    }
}

/**
 * Single Source of Truth Live Viewfinder Overlay for Reference Image 1 (Brebes Satellite Style):
 * 1. wrapContentHeight() on the text card (no fixed height, zero black empty gap at bottom).
 * 2. Inner padding = 6.dp (top, bottom, start, end).
 * 3. Typography scale:
 *    - Header (2 lines: "[Kecamatan/Kota], [Provinsi]," / "Indonesia 🇮🇩"): 13.sp Bold (#FFFFFF, maxLines = 2)
 *    - Address & Plus Code: 10.sp Regular (#E5E5E5, lineHeight = 12.sp)
 *    - Lat/Long: 9.5.sp (#E5E5E5)
 *    - Date & Time: 9.5.sp (#E5E5E5)
 *    - Note Watermark: 8.5.sp (#CCCCCC)
 * 4. 1:1 Square Mini-Map automatically matching the text card height, with 8.dp corner radius on both.
 */
@Composable
private fun Reference1ClassicLiveStamp(
    timeConfig: TimeConfig,
    location: LocationMetadata,
    styleConfig: StampStyleConfig,
    miniMapImageBitmap: ImageBitmap?,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFF282A2E).copy(alpha = styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f))
    val cardShape = RoundedCornerShape(8.dp)

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("live_stamp_overlay_card")
    ) {
        // Top-right attached tab badge: "[📷] GPS Map Camera"
        if (styleConfig.showBrandBadgeTab) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(cardBgColor)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF26A69A))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "GPS Map Camera",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 8.5.sp
                )
            }
        }

        // Main Split Row: Left 1:1 Square Satellite Mini-Map Card + Right Wrap-Content Info Card
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (styleConfig.showMiniMap && miniMapImageBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(cardShape)
                        .clickable(onClick = onLocationClick)
                ) {
                    Image(
                        bitmap = miniMapImageBitmap,
                        contentDescription = "Mini Map Satelit",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Right Dark Translucent Card hugging text content tightly (padding = 6.dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .clip(cardShape)
                    .background(cardBgColor)
                    .clickable(onClick = onLocationClick)
                    .padding(6.dp)
            ) {
                // Header Wilayah (2 Baris: "[Kecamatan/Kota], [Provinsi]," & "Indonesia 🇮🇩")
                val flagSuffix = if (location.showCountryFlag) " \uD83C\uDDEE\uD83C\uDDE9" else ""
                val twoLineHeader = "${location.template1HeaderLine1}\n${location.template1HeaderLine2}$flagSuffix"
                Text(
                    text = twoLineHeader,
                    color = Color(0xFFFFFFFF),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 14.8.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Detail Alamat & Plus Code
                Text(
                    text = location.template1FullAddressWithPlusCode,
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Koordinat Lat/Long
                Text(
                    text = location.template1LatLongText,
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp,
                    lineHeight = 11.2.sp,
                    maxLines = 1
                )

                // Tanggal & Waktu (clickable to open Time Override Modal)
                Text(
                    text = timeConfig.formatTemplate1Timestamp(),
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp,
                    lineHeight = 11.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onTimeClick)
                )

                // Note Watermark
                if (styleConfig.showProjectNote && location.projectNote.isNotBlank()) {
                    val noteDisplay = if (location.projectNote.startsWith("Note", ignoreCase = true)) {
                        location.projectNote
                    } else {
                        "Note : ${location.projectNote}"
                    }
                    Text(
                        text = noteDisplay,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 8.5.sp,
                        lineHeight = 10.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Single Source of Truth Live Viewfinder Overlay for Reference Image 2 (Street Map + Telemetry Sensor Icons):
 * - Left separate rounded card (8.dp): 1:1 Square Light Street Map matching text column height
 * - Right separate dark translucent card (8.dp): wrapContentHeight() with 6.dp inner padding
 */
@Composable
private fun Reference2TelemetryLiveStamp(
    timeConfig: TimeConfig,
    location: LocationMetadata,
    styleConfig: StampStyleConfig,
    miniMapImageBitmap: ImageBitmap?,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBgColor = Color(0xFF1C1E22).copy(alpha = styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f))
    val cardShape = RoundedCornerShape(8.dp)

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("live_stamp_overlay_card")
    ) {
        if (styleConfig.showMiniMap && miniMapImageBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(cardShape)
                    .clickable(onClick = onLocationClick)
            ) {
                Image(
                    bitmap = miniMapImageBitmap,
                    contentDescription = "Mini Map Jalan",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .clip(cardShape)
                .background(cardBgColor)
                .clickable(onClick = onLocationClick)
                .padding(6.dp)
        ) {
            // 2-line Address Header
            Text(
                text = location.template2AddressHeader,
                color = Color(0xFFFFFFFF),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Row 1: Crosshair + Comma Coordinates
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    tint = Color(0xFFE5E5E5),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = location.template2CommaCoordText,
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp,
                    lineHeight = 11.2.sp
                )
            }

            // Row 2: Clock + Timestamp with GMT offset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onTimeClick)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFFE5E5E5),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = timeConfig.formatTemplate2Timestamp(),
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp,
                    lineHeight = 11.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 3: Elevation ("44m di atas permukaan laut")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = Color(0xFFE5E5E5),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = location.template2AltitudeText,
                    color = Color(0xFFE5E5E5),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.5.sp,
                    lineHeight = 11.2.sp
                )
            }

            // Row 4: 3 Sensor Columns ("±2m akurasi" | "45,72 µT" | "12 N")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(10.5.dp)
                    )
                    Spacer(modifier = Modifier.width(3.5.dp))
                    Text(
                        text = location.template2AccuracyText,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.sp,
                        lineHeight = 10.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(10.5.dp)
                    )
                    Spacer(modifier = Modifier.width(3.5.dp))
                    Text(
                        text = location.template2MagneticText,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.sp,
                        lineHeight = 10.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = Color(0xFFCCCCCC),
                        modifier = Modifier.size(10.5.dp)
                    )
                    Spacer(modifier = Modifier.width(3.5.dp))
                    Text(
                        text = location.template2CompassText,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 9.sp,
                        lineHeight = 10.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalistLiveStamp(
    timeConfig: TimeConfig,
    location: LocationMetadata,
    styleConfig: StampStyleConfig,
    miniMapImageBitmap: ImageBitmap?,
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = Color(0xFF141820).copy(alpha = styleConfig.backgroundOpacity.coerceIn(0.40f, 0.95f))
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(shape)
            .background(bgColor)
            .padding(6.dp)
            .testTag("live_stamp_overlay_card")
    ) {
        if (styleConfig.showMiniMap && miniMapImageBitmap != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLocationClick)
            ) {
                Image(
                    bitmap = miniMapImageBitmap,
                    contentDescription = "Mini Map Lokasi GPS",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeConfig.formatTemplate1Timestamp(),
                color = SignalAmber,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onTimeClick)
            )
            Text(
                text = location.fullAddress,
                color = Color(0xFFE5E5E5),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onLocationClick)
            )
            Text(
                text = "${location.template1LatLongText} • ${location.template2MagneticText} • ${location.template2CompassText}",
                fontFamily = JetBrainsMonoFontFamily,
                color = GpsEmerald,
                fontSize = 9.sp
            )
        }
    }
}
