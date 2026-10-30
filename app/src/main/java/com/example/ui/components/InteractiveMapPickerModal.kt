package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.sensor.PlaceSearchResult
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
    MapQuickRegion("Slawi / Tegal", -6.978291, 109.140948, "Procot, Kecamatan Slawi, Kabupaten Tegal, Jawa Tengah 52411"),
    MapQuickRegion("Moskva (Ref 2)", 55.739900, 37.601600, "1-Y Zachat'yevskiy Pereulok, 15, Moskva, Rusia, 119034"),
    MapQuickRegion("Jakarta Monas", -6.175392, 106.827153, "Jl. Medan Merdeka Utara, Gambir, Jakarta Pusat"),
    MapQuickRegion("Surabaya", -7.257472, 112.752088, "Jl. Tunjungan No. 65, Genteng, Kota Surabaya")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveMapPickerDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    initialAddress: String,
    mapTileFetcher: MapTileFetcher,
    onReverseGeocodeRequest: suspend (Double, Double) -> GeocodedAddressParts?,
    onSearchPlacesRequest: suspend (String) -> List<PlaceSearchResult> = { emptyList() },
    onRequestMyGpsLocation: suspend () -> Pair<Double, Double>?,
    onDismissCancel: () -> Unit,
    onConfirmLocation: (PickedMapLocationResult) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Draft Map Viewport Center & Zoom (only committed when user clicks "Pilih Lokasi / OK")
    var viewportCenterLat by remember { mutableDoubleStateOf(initialLatitude.coerceIn(-85.0, 85.0)) }
    var viewportCenterLon by remember { mutableDoubleStateOf(initialLongitude.coerceIn(-180.0, 180.0)) }
    var zoomLevel by remember { mutableIntStateOf(16) }
    var isSatelliteLayer by remember { mutableStateOf(false) }

    // Draft Selected Marker Point (can be placed by tapping anywhere on the map, dragging, or searching)
    var selectedLat by remember { mutableDoubleStateOf(initialLatitude.coerceIn(-85.0, 85.0)) }
    var selectedLon by remember { mutableDoubleStateOf(initialLongitude.coerceIn(-180.0, 180.0)) }
    var draftAddress by remember { mutableStateOf(initialAddress) }
    var draftGeocodedParts by remember { mutableStateOf<GeocodedAddressParts?>(null) }
    var isGeocodingLoading by remember { mutableStateOf(false) }
    var lockMarkerToCenterOnPan by remember { mutableStateOf(true) }

    // Place Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var isSearchingPlaces by remember { mutableStateOf(false) }
    var showSearchResultsDropdown by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize(800, 800)) }
    // Pre-converted ImageBitmap cache so Canvas onDraw runs at 60 FPS without bitmap wrapping
    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    val draftPlusCode by remember {
        derivedStateOf {
            PlusCodeGenerator.generateShortPlusCode(selectedLat, selectedLon)
        }
    }

    // Derive integer tile indices so dragging inside the same 256px tile does NOT spawn coroutines on every pixel
    val currentTileKey by remember {
        derivedStateOf {
            val (cx, cy) = WebMercatorMath.latLonToExactTile(viewportCenterLat, viewportCenterLon, zoomLevel)
            Triple(floor(cx).toInt(), floor(cy).toInt(), zoomLevel)
        }
    }

    fun triggerDraftReverseGeocode(lat: Double, lon: Double, fallbackHint: String = "") {
        geocodeJob?.cancel()
        geocodeJob = coroutineScope.launch {
            isGeocodingLoading = true
            delay(320) // Debounce while user drags or taps rapidly
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

    fun executePlaceSearch(query: String, immediate: Boolean = false) {
        searchJob?.cancel()
        val cleaned = query.trim()
        if (cleaned.length < 2) {
            searchResults = emptyList()
            showSearchResultsDropdown = false
            isSearchingPlaces = false
            return
        }
        searchJob = coroutineScope.launch {
            isSearchingPlaces = true
            if (!immediate) {
                delay(350)
            }
            val found = onSearchPlacesRequest(cleaned)
            searchResults = found
            showSearchResultsDropdown = found.isNotEmpty()
            isSearchingPlaces = false
        }
    }

    fun selectPlaceFromSearchResult(item: PlaceSearchResult) {
        focusManager.clearFocus()
        showSearchResultsDropdown = false
        viewportCenterLat = item.latitude.coerceIn(-85.0, 85.0)
        viewportCenterLon = item.longitude.coerceIn(-180.0, 180.0)
        selectedLat = viewportCenterLat
        selectedLon = viewportCenterLon
        if (zoomLevel < 16) zoomLevel = 16
        if (item.addressParts != null) {
            draftGeocodedParts = item.addressParts
            draftAddress = item.subtitle.ifBlank { item.title }
        } else {
            draftAddress = item.subtitle.ifBlank { item.title }
            triggerDraftReverseGeocode(viewportCenterLat, viewportCenterLon, draftAddress)
        }
    }

    // Load visible 256x256 slippy map tiles ONLY when integer tile index, zoom, or layer changes
    LaunchedEffect(currentTileKey, isSatelliteLayer) {
        val (baseX, baseY, z) = currentTileKey
        val prefix = if (isSatelliteLayer) "sat" else "osm"

        for (dx in -1..1) {
            for (dy in -1..1) {
                val tx = baseX + dx
                val ty = baseY + dy
                val key = "${prefix}_${z}_${tx}_${ty}"
                if (!loadedTiles.containsKey(key)) {
                    launch {
                        val bmp = mapTileFetcher.fetchTileForInteractiveMap(
                            zoom = z,
                            tileX = tx,
                            tileY = ty,
                            isSatellite = isSatelliteLayer
                        )
                        if (bmp != null && !bmp.isRecycled) {
                            loadedTiles[key] = bmp.asImageBitmap()
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
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Pilih Lokasi Manual di Peta",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Cari nama tempat/jalan, geser peta, atau ketuk titik lokasi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 11.sp
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

            // 2. Search Place / Address Bar + Quick Jump Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TacticalSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newText ->
                            searchQuery = newText
                            executePlaceSearch(newText, immediate = false)
                        },
                        placeholder = {
                            Text(
                                text = "Cari tempat, jalan, kecamatan, kota...",
                                fontSize = 12.5.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari Lokasi",
                                tint = CompassCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (isSearchingPlaces) {
                                CircularProgressIndicator(
                                    color = SignalAmber,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        showSearchResultsDropdown = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Hapus Pencarian",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                executePlaceSearch(searchQuery, immediate = true)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ObsidianBackground,
                            unfocusedContainerColor = ObsidianBackground,
                            focusedBorderColor = CompassCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            executePlaceSearch(searchQuery, immediate = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CompassCyan,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("map_search_submit_button")
                    ) {
                        Text(
                            text = "Cari",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Dropdown list of matching places from Search
                if (showSearchResultsDropdown && searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CompassCyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .testTag("map_search_results_list")
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 190.dp)
                        ) {
                            items(searchResults) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectPlaceFromSearchResult(item) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .testTag("map_search_result_item")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = SignalAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickMapRegions.forEach { region ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                showSearchResultsDropdown = false
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

            // 3. Interactive Multi-Tile Map Canvas (60 FPS Pre-Cached ImageBitmap Rendering)
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
                            onDragStart = {
                                showSearchResultsDropdown = false
                            },
                            onDragEnd = {
                                if (lockMarkerToCenterOnPan) {
                                    triggerDraftReverseGeocode(selectedLat, selectedLon)
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val tileRenderPx = 512.0
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
                            showSearchResultsDropdown = false
                            focusManager.clearFocus()
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

                    // Draw surrounding slippy map tiles using pre-cached ImageBitmap (zero allocation in onDraw)
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val tx = baseX + dx
                            val ty = baseY + dy
                            val screenLeft = viewCenterX + ((tx - centerTileX) * tileRenderPx).toFloat()
                            val screenTop = viewCenterY + ((ty - centerTileY) * tileRenderPx).toFloat()

                            val key = "${prefix}_${zoomLevel}_${tx}_${ty}"
                            val tileImg = loadedTiles[key]
                            if (tileImg != null) {
                                drawImage(
                                    image = tileImg,
                                    dstOffset = IntOffset(screenLeft.roundToInt(), screenTop.roundToInt()),
                                    dstSize = IntSize(tileRenderPx.roundToInt() + 1, tileRenderPx.roundToInt() + 1)
                                )
                            } else {
                                // Offline fallback grid tile
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
                        radius = 32.dp.toPx(),
                        center = Offset(markerX, markerY)
                    )
                    drawCircle(
                        color = Color(0xFFEA4335),
                        radius = 32.dp.toPx(),
                        center = Offset(markerX, markerY),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw Teardrop Pin Marker with tip exactly at (markerX, markerY)
                    val pinH = 42.dp.toPx()
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
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = SignalAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Zoom $zoomLevel • Ketuk / Geser Peta",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.5.sp,
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
                    .padding(14.dp)
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

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = String.format(Locale.US, "Lat %.6f° • Long %.6f°", selectedLat, selectedLon),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = CompassCyan,
                    modifier = Modifier.testTag("map_selected_coordinates_text")
                )

                Spacer(modifier = Modifier.height(3.dp))

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

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismissCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
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
                            .height(48.dp)
                            .testTag("map_confirm_ok_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pilih Lokasi (OK)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
