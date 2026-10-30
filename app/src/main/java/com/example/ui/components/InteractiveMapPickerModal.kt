package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.model.LocationSource
import com.example.model.PlusCodeGenerator
import com.example.sensor.GeocodedAddressParts
import com.example.stamp.MapTileFetcher
import com.example.stamp.WebMercatorMath
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

data class PickedMapLocationResult(
    val latitude: Double,
    val longitude: Double,
    val plusCode: String,
    val addressText: String,
    val geocodedParts: GeocodedAddressParts?,
    val locationSource: String = LocationSource.MANUAL_MAP
)

private data class MapQuickRegion(
    val name: String,
    val lat: Double,
    val lon: Double,
    val defaultAddress: String
)

private val quickMapRegions = listOf(
    MapQuickRegion("Brebes (Ref 1)", -6.887405, 109.035938, "Jl. Sapphire Regency, Pulosari, Kec. Brebes, Jawa Tengah 52213"),
    MapQuickRegion("Moskva (Ref 2)", 55.739900, 37.601600, "1-Y Zachat'yevskiy Pereulok, 15, Moskva, Rusia, 119034"),
    MapQuickRegion("Jakarta Monas", -6.175392, 106.827153, "Jl. Medan Merdeka Utara, Gambir, Jakarta Pusat"),
    MapQuickRegion("Surabaya", -7.257472, 112.752088, "Jl. Tunjungan No. 65, Genteng, Kota Surabaya"),
    MapQuickRegion("Bandung", -6.902481, 107.618810, "Jl. Diponegoro No. 22, Citarum, Kota Bandung")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveMapPickerDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    initialAddress: String,
    mapTileFetcher: MapTileFetcher,
    onReverseGeocodeRequest: suspend (Double, Double) -> GeocodedAddressParts?,
    onRequestMyGpsLocation: suspend () -> Pair<Double, Double>?,
    onDismissCancel: () -> Unit,
    onConfirmLocation: (PickedMapLocationResult) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Draft Map Viewport Center & Zoom (only committed when user clicks "Pilih Lokasi / OK")
    var viewportCenterLat by remember { mutableDoubleStateOf(initialLatitude.coerceIn(-85.0, 85.0)) }
    var viewportCenterLon by remember { mutableDoubleStateOf(initialLongitude.coerceIn(-180.0, 180.0)) }
    var zoomLevel by remember { mutableIntStateOf(16) }
    var isSatelliteLayer by remember { mutableStateOf(false) }

    // Draft Selected Marker Point (can be placed by tapping anywhere on the map or dragging)
    var selectedLat by remember { mutableDoubleStateOf(initialLatitude.coerceIn(-85.0, 85.0)) }
    var selectedLon by remember { mutableDoubleStateOf(initialLongitude.coerceIn(-180.0, 180.0)) }
    var draftAddress by remember { mutableStateOf(initialAddress) }
    var draftGeocodedParts by remember { mutableStateOf<GeocodedAddressParts?>(null) }
    var isGeocodingLoading by remember { mutableStateOf(false) }
    var lockMarkerToCenterOnPan by remember { mutableStateOf(true) }

    var canvasSize by remember { mutableStateOf(IntSize(800, 800)) }
    val loadedTiles = remember { mutableStateMapOf<String, Bitmap>() }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    val draftPlusCode = remember(selectedLat, selectedLon) {
        PlusCodeGenerator.generateShortPlusCode(selectedLat, selectedLon)
    }

    fun triggerDraftReverseGeocode(lat: Double, lon: Double, fallbackHint: String = "") {
        geocodeJob?.cancel()
        geocodeJob = coroutineScope.launch {
            isGeocodingLoading = true
            delay(280) // Debounce while user drags or taps rapidly
            val parts = onReverseGeocodeRequest(lat, lon)
            draftGeocodedParts = parts
            draftAddress = when {
                parts != null && parts.formattedAddress.isNotBlank() -> parts.formattedAddress
                parts != null -> listOf(
                    parts.street,
                    parts.subLocality,
                    parts.locality,
                    parts.city,
                    parts.province,
                    parts.postalCode,
                    parts.country
                ).filter { it.isNotBlank() }.joinToString(", ")
                fallbackHint.isNotBlank() -> fallbackHint
                else -> String.format(Locale.US, "Koordinat Terpilih (%.6f, %.6f)", lat, lon)
            }
            isGeocodingLoading = false
        }
    }

    // Load visible 256x256 slippy map tiles whenever viewportCenterLat, viewportCenterLon, zoom, or layer changes
    LaunchedEffect(viewportCenterLat, viewportCenterLon, zoomLevel, isSatelliteLayer) {
        val (centerTileX, centerTileY) = WebMercatorMath.latLonToExactTile(
            viewportCenterLat,
            viewportCenterLon,
            zoomLevel
        )
        val baseX = floor(centerTileX).toInt()
        val baseY = floor(centerTileY).toInt()
        val prefix = if (isSatelliteLayer) "sat" else "osm"

        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = baseX + dx
                val ty = baseY + dy
                val key = "${prefix}_${zoomLevel}_${tx}_${ty}"
                if (!loadedTiles.containsKey(key)) {
                    launch {
                        val bmp = mapTileFetcher.fetchTileForInteractiveMap(
                            zoom = zoomLevel,
                            tileX = tx,
                            tileY = ty,
                            isSatellite = isSatelliteLayer
                        )
                        if (bmp != null) {
                            loadedTiles[key] = bmp
                        }
                    }
                }
            }
        }
    }

    // Permission launcher ONLY for optional "Lokasi Saya" button (never required to use the map!)
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch {
                val myLoc = onRequestMyGpsLocation()
                if (myLoc != null) {
                    viewportCenterLat = myLoc.first
                    viewportCenterLon = myLoc.second
                    selectedLat = myLoc.first
                    selectedLon = myLoc.second
                    triggerDraftReverseGeocode(myLoc.first, myLoc.second)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("interactive_map_picker_dialog")
        ) {
            // 1. Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pilih Lokasi Manual di Peta",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Geser peta atau ketuk titik untuk menentukan lokasi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                IconButton(
                    onClick = onDismissCancel,
                    modifier = Modifier.testTag("map_header_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Batal", tint = TextSecondary)
                }
            }

            // 2. Quick Jump Bar (Helps user quickly jump between cities/regions before fine-tuning on map)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickMapRegions.forEach { region ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewportCenterLat = region.lat
                                viewportCenterLon = region.lon
                                selectedLat = region.lat
                                selectedLon = region.lon
                                draftAddress = region.defaultAddress
                                triggerDraftReverseGeocode(region.lat, region.lon, region.defaultAddress)
                            },
                            label = { Text(region.name, fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ObsidianBackground,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("map_preset_${region.name.take(6).lowercase(Locale.US)}")
                        )
                    }
                }
            }

            // 3. Interactive Multi-Tile Map Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            canvasSize = size
                        }
                    }
                    .pointerInput(zoomLevel, lockMarkerToCenterOnPan) {
                        detectDragGestures(
                            onDragEnd = {
                                if (lockMarkerToCenterOnPan) {
                                    triggerDraftReverseGeocode(selectedLat, selectedLon)
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val tileRenderPx = 512.0 // Crisp 2x density tile rendering
                            val (curTileX, curTileY) = WebMercatorMath.latLonToExactTile(
                                viewportCenterLat,
                                viewportCenterLon,
                                zoomLevel
                            )
                            val newTileX = curTileX - (dragAmount.x / tileRenderPx)
                            val newTileY = curTileY - (dragAmount.y / tileRenderPx)
                            val (newLat, newLon) = WebMercatorMath.exactTileToLatLon(
                                newTileX,
                                newTileY,
                                zoomLevel
                            )
                            viewportCenterLat = newLat
                            viewportCenterLon = newLon
                            if (lockMarkerToCenterOnPan) {
                                selectedLat = newLat
                                selectedLon = newLon
                            }
                        }
                    }
                    .pointerInput(zoomLevel, canvasSize) {
                        detectTapGestures { tapOffset ->
                            val tileRenderPx = 512.0
                            val centerX = canvasSize.width / 2.0
                            val centerY = canvasSize.height / 2.0
                            val dxPx = tapOffset.x - centerX
                            val dyPx = tapOffset.y - centerY

                            val (centerTileX, centerTileY) = WebMercatorMath.latLonToExactTile(
                                viewportCenterLat,
                                viewportCenterLon,
                                zoomLevel
                            )
                            val tappedTileX = centerTileX + (dxPx / tileRenderPx)
                            val tappedTileY = centerTileY + (dyPx / tileRenderPx)
                            val (tappedLat, tappedLon) = WebMercatorMath.exactTileToLatLon(
                                tappedTileX,
                                tappedTileY,
                                zoomLevel
                            )
                            selectedLat = tappedLat
                            selectedLon = tappedLon
                            if (lockMarkerToCenterOnPan) {
                                viewportCenterLat = tappedLat
                                viewportCenterLon = tappedLon
                            }
                            triggerDraftReverseGeocode(tappedLat, tappedLon)
                        }
                    }
                    .testTag("interactive_map_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tileRenderPx = 512f
                    val viewCenterX = size.width / 2f
                    val viewCenterY = size.height / 2f

                    // Base background
                    drawRect(
                        color = if (isSatelliteLayer) Color(0xFF1E2D24) else Color(0xFFEAECEF)
                    )

                    val (centerTileX, centerTileY) = WebMercatorMath.latLonToExactTile(
                        viewportCenterLat,
                        viewportCenterLon,
                        zoomLevel
                    )
                    val baseX = floor(centerTileX).toInt()
                    val baseY = floor(centerTileY).toInt()
                    val prefix = if (isSatelliteLayer) "sat" else "osm"

                    // Draw 3x3 surrounding slippy map tiles
                    for (dx in -2..2) {
                        for (dy in -2..2) {
                            val tx = baseX + dx
                            val ty = baseY + dy
                            val screenLeft = viewCenterX + ((tx - centerTileX) * tileRenderPx).toFloat()
                            val screenTop = viewCenterY + ((ty - centerTileY) * tileRenderPx).toFloat()

                            val key = "${prefix}_${zoomLevel}_${tx}_${ty}"
                            val tileBmp = loadedTiles[key]
                            if (tileBmp != null && !tileBmp.isRecycled) {
                                drawImage(
                                    image = tileBmp.asImageBitmap(),
                                    dstOffset = IntOffset(screenLeft.roundToInt(), screenTop.roundToInt()),
                                    dstSize = IntSize(tileRenderPx.roundToInt() + 1, tileRenderPx.roundToInt() + 1)
                                )
                            } else {
                                // Offline fallback grid tile with subtle street contours
                                val gridColor = if (isSatelliteLayer) {
                                    Color(0xFF334E3C)
                                } else {
                                    Color(0xFFD0D7DE)
                                }
                                drawRect(
                                    color = gridColor,
                                    topLeft = Offset(screenLeft, screenTop),
                                    size = androidx.compose.ui.geometry.Size(tileRenderPx, tileRenderPx),
                                    style = Stroke(width = 1.5f)
                                )
                                // Sub-grid lines
                                val subStep = tileRenderPx / 4f
                                for (s in 1..3) {
                                    drawLine(
                                        color = gridColor.copy(alpha = 0.55f),
                                        start = Offset(screenLeft + s * subStep, screenTop),
                                        end = Offset(screenLeft + s * subStep, screenTop + tileRenderPx),
                                        strokeWidth = 1f
                                    )
                                    drawLine(
                                        color = gridColor.copy(alpha = 0.55f),
                                        start = Offset(screenLeft, screenTop + s * subStep),
                                        end = Offset(screenLeft + tileRenderPx, screenTop + s * subStep),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                        }
                    }

                    // Compute screen pixel position of the selected marker (selectedLat, selectedLon)
                    val (selTileX, selTileY) = WebMercatorMath.latLonToExactTile(
                        selectedLat,
                        selectedLon,
                        zoomLevel
                    )
                    val markerX = viewCenterX + ((selTileX - centerTileX) * tileRenderPx).toFloat()
                    val markerY = viewCenterY + ((selTileY - centerTileY) * tileRenderPx).toFloat()

                    // Draw pulsing target circle & crosshair at selected point
                    drawCircle(
                        color = Color(0x44EA4335),
                        radius = 34.dp.toPx(),
                        center = Offset(markerX, markerY)
                    )
                    drawCircle(
                        color = Color(0xFFEA4335),
                        radius = 34.dp.toPx(),
                        center = Offset(markerX, markerY),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw Teardrop Pin Marker with tip exactly at (markerX, markerY)
                    val pinH = 44.dp.toPx()
                    val pinR = pinH * 0.36f
                    val headCenterY = markerY - pinH * 0.64f
                    val pinPath = Path().apply {
                        moveTo(markerX, markerY)
                        lineTo(markerX - pinR * 0.85f, headCenterY + pinR * 0.35f)
                        lineTo(markerX + pinR * 0.85f, headCenterY + pinR * 0.35f)
                        close()
                    }
                    drawPath(pinPath, color = Color(0xFFEA4335))
                    drawCircle(
                        color = Color(0xFFEA4335),
                        radius = pinR,
                        center = Offset(markerX, headCenterY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = pinR,
                        center = Offset(markerX, headCenterY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF7A1812),
                        radius = pinR * 0.38f,
                        center = Offset(markerX, headCenterY)
                    )
                }

                // Top-left Mode Badge ("Ketuk Peta / Geser Pin" + Zoom indicator)
                Surface(
                    color = ObsidianBackground.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = SignalAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Zoom $zoomLevel • Ketuk / Geser Peta",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                // Right-side Map Controls: Layer Switch | Zoom In (+) | Zoom Out (-) | Lokasi Saya (GPS helper)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                ) {
                    // Layer Toggle (OSM Street vs Satellite)
                    IconButton(
                        onClick = { isSatelliteLayer = !isSatelliteLayer },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground.copy(alpha = 0.88f))
                            .border(1.dp, BorderSubtle, CircleShape)
                            .testTag("map_toggle_layer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Ganti Lapisan Peta",
                            tint = if (isSatelliteLayer) SignalAmber else CompassCyan
                        )
                    }

                    // Zoom In (+)
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel + 1).coerceIn(4, 18) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground.copy(alpha = 0.88f))
                            .border(1.dp, BorderSubtle, CircleShape)
                            .testTag("map_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Perbesar Peta (Zoom In)",
                            tint = Color.White
                        )
                    }

                    // Zoom Out (-)
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel - 1).coerceIn(4, 18) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground.copy(alpha = 0.88f))
                            .border(1.dp, BorderSubtle, CircleShape)
                            .testTag("map_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Perkecil Peta (Zoom Out)",
                            tint = Color.White
                        )
                    }

                    // Optional "Lokasi Saya" GPS helper button
                    IconButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasFine || hasCoarse) {
                                coroutineScope.launch {
                                    val myLoc = onRequestMyGpsLocation()
                                    if (myLoc != null) {
                                        viewportCenterLat = myLoc.first
                                        viewportCenterLon = myLoc.second
                                        selectedLat = myLoc.first
                                        selectedLon = myLoc.second
                                        triggerDraftReverseGeocode(myLoc.first, myLoc.second)
                                    }
                                }
                            } else {
                                gpsPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground.copy(alpha = 0.88f))
                            .border(1.dp, GpsEmerald, CircleShape)
                            .testTag("map_my_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Lokasi Saya (Bantuan GPS)",
                            tint = GpsEmerald
                        )
                    }
                }
            }

            // 4. Bottom Selected Point Info & Confirmation Panel ("Batal" vs "Pilih Lokasi / OK")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurface)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TITIK LOKASI YANG DIPILIH",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = SignalAmber
                    )
                    Text(
                        text = "Plus Code: $draftPlusCode",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = GpsEmerald
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = String.format(Locale.US, "Lat %.6f° • Long %.6f°", selectedLat, selectedLon),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CompassCyan,
                    modifier = Modifier.testTag("map_selected_coordinates_text")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGeocodingLoading) {
                        CircularProgressIndicator(
                            color = SignalAmber,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mencari alamat (reverse geocoding)...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    } else {
                        Text(
                            text = draftAddress.ifBlank {
                                String.format(Locale.US, "Koordinat %.6f, %.6f", selectedLat, selectedLon)
                            },
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("map_selected_address_text")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismissCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("map_cancel_button")
                    ) {
                        Text(
                            text = "Batal",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            onConfirmLocation(
                                PickedMapLocationResult(
                                    latitude = selectedLat,
                                    longitude = selectedLon,
                                    plusCode = draftPlusCode,
                                    addressText = draftAddress.ifBlank {
                                        String.format(Locale.US, "%.6f, %.6f", selectedLat, selectedLon)
                                    },
                                    geocodedParts = draftGeocodedParts,
                                    locationSource = LocationSource.MANUAL_MAP
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalAmber,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                            .testTag("map_confirm_ok_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pilih Lokasi (OK)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }
        }
    }
}
