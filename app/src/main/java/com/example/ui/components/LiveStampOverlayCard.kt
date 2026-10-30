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
 * 100% Reference Image 1 (Green-Circled Brebes Satellite Style) Live Viewfinder Overlay:
 * - Left separate rounded card: 1:1 Square Satellite Mini-Map with Center-Crop (never squished)
 * - Top-right attached badge tab: "[📷] GPS Map Camera"
 * - Right separate dark translucent card: Full-space proportional typography matching Green Circle reference
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
    val cardShape = RoundedCornerShape(7.dp)

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_stamp_overlay_card")
    ) {
        // Top-right attached tab badge: "[📷] GPS Map Camera"
        if (styleConfig.showBrandBadgeTab) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 5.dp)
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(cardBgColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF26A69A))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(7.5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(3.5.dp))
                Text(
                    text = "GPS Map Camera",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 8.2.sp
                )
            }
        }

        // Main Split Row: Left 1:1 Square Satellite Mini-Map Card + Right Compact Info Card
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
                Spacer(modifier = Modifier.width(5.dp))
            }

            // Right Dark Translucent Card (1:1 Calibrated Proportions)
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(cardShape)
                    .background(cardBgColor)
                    .clickable(onClick = onLocationClick)
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                // Title + Flag (up to 2 lines matching original stamp)
                val flagSuffix = if (location.showCountryFlag) " \uD83C\uDDEE\uD83C\uDDE9" else ""
                Text(
                    text = "${location.template1Headline}$flagSuffix",
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    lineHeight = 15.2.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Plus Code + Full Address
                Text(
                    text = location.template1FullAddressWithPlusCode,
                    color = Color(0xFFF3F4F6),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.6.sp,
                    lineHeight = 11.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Lat / Long line
                Text(
                    text = location.template1LatLongText,
                    color = Color(0xFFF3F4F6),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.0.sp,
                    lineHeight = 10.8.sp,
                    maxLines = 1
                )

                // Timestamp line (clickable to open Time Override Modal)
                Text(
                    text = timeConfig.formatTemplate1Timestamp(),
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 9.0.sp,
                    lineHeight = 10.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onTimeClick)
                )

                // Note line
                if (styleConfig.showProjectNote && location.projectNote.isNotBlank()) {
                    val noteDisplay = if (location.projectNote.startsWith("Note", ignoreCase = true)) {
                        location.projectNote
                    } else {
                        "Note : ${location.projectNote}"
                    }
                    Text(
                        text = noteDisplay,
                        color = Color(0xFFF3F4F6),
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
 * 100% Reference Image 2 (Street Map + Telemetry Sensor Icons Style) Live Viewfinder Overlay:
 * - Left separate rounded card: 1:1 Square Light Street Map with Center-Crop (never squished)
 * - Right separate dark translucent card: Compact low-profile strip matching BitmapStampRenderer
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
            Spacer(modifier = Modifier.width(5.dp))
        }

        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(cardBgColor)
                .clickable(onClick = onLocationClick)
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            // 2-line Address Header
            Text(
                text = location.template2AddressHeader,
                color = Color.White,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                lineHeight = 10.8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(1.5.dp))

            // Row 1: Crosshair + Comma Coordinates
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.5.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = location.template2CommaCoordText,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    lineHeight = 9.sp
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
                    tint = Color.White,
                    modifier = Modifier.size(9.5.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = timeConfig.formatTemplate2Timestamp(),
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Row 3: Elevation ("44m di atas permukaan laut")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Landscape,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.5.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = location.template2AltitudeText,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    lineHeight = 9.sp
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
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = location.template2AccuracyText,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 7.8.sp,
                        lineHeight = 8.8.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = location.template2MagneticText,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 7.8.sp,
                        lineHeight = 8.8.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = location.template2CompassText,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 7.8.sp,
                        lineHeight = 8.8.sp
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
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("live_stamp_overlay_card")
    ) {
        if (styleConfig.showMiniMap && miniMapImageBitmap != null) {
            Box(
                modifier = Modifier
                    .size(54.dp)
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
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeConfig.formatTemplate1Timestamp(),
                color = SignalAmber,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                modifier = Modifier.clickable(onClick = onTimeClick)
            )
            Text(
                text = location.fullAddress,
                color = Color.White,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onLocationClick)
            )
            Text(
                text = "${location.template1LatLongText} • ${location.template2MagneticText} • ${location.template2CompassText}",
                fontFamily = JetBrainsMonoFontFamily,
                color = GpsEmerald,
                fontSize = 9.5.sp
            )
        }
    }
}
