package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LocationMetadata
import com.example.model.LocationSource
import com.example.model.MiniMapStyle
import com.example.model.PlusCodeGenerator
import com.example.model.StampPosition
import com.example.model.StampStyleConfig
import com.example.model.StampTemplate
import com.example.sensor.GeocodedAddressParts
import com.example.sensor.PlaceSearchResult
import com.example.stamp.MapTileFetcher
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

private data class PresetReferenceLocation(
    val label: String,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val plusCode: String,
    val street: String,
    val subLocality: String,
    val locality: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String,
    val showFlag: Boolean,
    val note: String
)

private val presetLocations = listOf(
    PresetReferenceLocation(
        label = "Brebes (Gambar Ref 1)",
        lat = -6.887405,
        lon = 109.035938,
        alt = 44.0,
        plusCode = "427p+2cv",
        street = "Jl. Sapphire Regency",
        subLocality = "Pulosari",
        locality = "Kecamatan Brebes",
        city = "Kabupaten Brebes",
        province = "Jawa Tengah",
        postalCode = "52213",
        country = "Indonesia",
        showFlag = true,
        note = "Captured by GPS Map Camera"
    ),
    PresetReferenceLocation(
        label = "Moskva (Gambar Ref 2)",
        lat = 55.739900,
        lon = 37.601600,
        alt = 44.0,
        plusCode = "pjq2+xj",
        street = "1-Y Zachat'yevskiy Pereulok",
        subLocality = "15",
        locality = "Moskva",
        city = "Moskva",
        province = "Moskva",
        postalCode = "119034",
        country = "Rusia",
        showFlag = false,
        note = "Captured by GPS Map Camera"
    ),
    PresetReferenceLocation(
        label = "Jakarta (Monas)",
        lat = -6.175392,
        lon = 106.827153,
        alt = 14.5,
        plusCode = "rref+rqv",
        street = "Jl. Medan Merdeka Utara No. 3",
        subLocality = "Gambir",
        locality = "Kecamatan Gambir",
        city = "Jakarta Pusat",
        province = "DKI Jakarta",
        postalCode = "10110",
        country = "Indonesia",
        showFlag = true,
        note = "Captured by GPS Map Camera"
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationEditModalSheet(
    location: LocationMetadata,
    mapTileFetcher: MapTileFetcher,
    onReverseGeocodeRequest: suspend (Double, Double) -> GeocodedAddressParts?,
    onSearchPlacesRequest: suspend (String) -> List<PlaceSearchResult> = { emptyList() },
    onRequestMyGpsLocation: suspend () -> Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onSaveLocation: (
        isManual: Boolean,
        locationSource: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        plusCode: String,
        street: String,
        subLocality: String,
        locality: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        showCountryFlag: Boolean,
        projectNote: String,
        customFullAddressOverride: String
    ) -> Unit,
    onTriggerReverseGeocode: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isManual by remember(location) { mutableStateOf(location.isManualLocation) }
    var locationSource by remember(location) { mutableStateOf(location.locationSource) }
    var latText by remember(location) { mutableStateOf(location.latitude.toString()) }
    var lonText by remember(location) { mutableStateOf(location.longitude.toString()) }
    var altText by remember(location) { mutableStateOf(location.altitudeMeters.toString()) }
    var plusCode by remember(location) { mutableStateOf(location.plusCode) }
    var street by remember(location) { mutableStateOf(location.street) }
    var subLocality by remember(location) { mutableStateOf(location.subLocality) }
    var locality by remember(location) { mutableStateOf(location.locality) }
    var city by remember(location) { mutableStateOf(location.city) }
    var province by remember(location) { mutableStateOf(location.province) }
    var postalCode by remember(location) { mutableStateOf(location.postalCode) }
    var country by remember(location) { mutableStateOf(location.country) }
    var showCountryFlag by remember(location) { mutableStateOf(location.showCountryFlag) }
    var projectNote by remember(location) { mutableStateOf(location.projectNote) }
    var customFullAddressOverride by remember(location) {
        mutableStateOf(location.customFullAddressOverride)
    }

    var showInteractiveMapPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TacticalSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EditLocationAlt,
                        contentDescription = null,
                        tint = CompassCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Formulir Lokasi, Peta & Catatan",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Sumber aktif: ${locationSource.uppercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (locationSource == LocationSource.MANUAL_MAP) SignalAmber else GpsEmerald
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary CTA: Choose Location Manually on Interactive Map
            Button(
                onClick = { showInteractiveMapPicker = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CompassCyan,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("open_interactive_map_picker_button")
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pilih Lokasi Manual melalui Peta Interaktif",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Switch Live GPS vs Manual Override
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TacticalSurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kunci Lokasi Pilihan (Jangan Timpa GPS)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (isManual) {
                            "Terkunci (${locationSource}): tidak akan berubah otomatis ke GPS perangkat"
                        } else {
                            "Mengikuti sensor GPS live perangkat secara otomatis"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = isManual,
                    onCheckedChange = { checked ->
                        isManual = checked
                        locationSource = if (checked) {
                            if (locationSource == LocationSource.GPS) LocationSource.MANUAL_FORM else locationSource
                        } else {
                            LocationSource.GPS
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = SignalAmber
                    ),
                    modifier = Modifier.testTag("manual_location_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Preset Cepat Sesuai Gambar Referensi:",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presetLocations.forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            isManual = true
                            locationSource = LocationSource.MANUAL_FORM
                            latText = preset.lat.toString()
                            lonText = preset.lon.toString()
                            altText = preset.alt.toString()
                            plusCode = preset.plusCode
                            street = preset.street
                            subLocality = preset.subLocality
                            locality = preset.locality
                            city = preset.city
                            province = preset.province
                            postalCode = preset.postalCode
                            country = preset.country
                            showCountryFlag = preset.showFlag
                            projectNote = preset.note
                            customFullAddressOverride = ""
                        },
                        label = { Text(preset.label, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Coordinates Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it
                        isManual = true
                        if (locationSource == LocationSource.GPS) locationSource = LocationSource.MANUAL_FORM
                    },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("latitude_input")
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = {
                        lonText = it
                        isManual = true
                        if (locationSource == LocationSource.GPS) locationSource = LocationSource.MANUAL_FORM
                    },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("longitude_input")
                )
                OutlinedTextField(
                    value = altText,
                    onValueChange = {
                        altText = it
                        isManual = true
                    },
                    label = { Text("Alt (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(0.75f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plus Code Row with Automatic Generator button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = plusCode,
                    onValueChange = { plusCode = it },
                    label = { Text("Plus Code (mis. 427p+2cv)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("plus_code_input")
                )
                OutlinedButton(
                    onClick = {
                        val lat = latText.toDoubleOrNull() ?: location.latitude
                        val lon = lonText.toDoubleOrNull() ?: location.longitude
                        plusCode = PlusCodeGenerator.generateShortPlusCode(lat, lon)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = SignalAmber)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto", color = SignalAmber, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onTriggerReverseGeocode,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = GpsEmerald)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ambil Alamat Otomatis dari Koordinat (Reverse Geocode)", color = GpsEmerald)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Address components
            OutlinedTextField(
                value = street,
                onValueChange = {
                    street = it
                    customFullAddressOverride = ""
                    isManual = true
                },
                label = { Text("Nama Jalan & Nomor") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("street_input")
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = subLocality,
                    onValueChange = {
                        subLocality = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Kelurahan / Desa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = locality,
                    onValueChange = {
                        locality = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Kecamatan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = {
                        city = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Kota / Kabupaten") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = province,
                    onValueChange = {
                        province = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Provinsi") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = {
                        postalCode = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Kode Pos") },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = {
                        country = it
                        customFullAddressOverride = ""
                        isManual = true
                    },
                    label = { Text("Negara") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Flag toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tampilkan Ikon Bendera Negara (🇮🇩) di Template 1",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Switch(
                    checked = showCountryFlag,
                    onCheckedChange = { showCountryFlag = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = GpsEmerald
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Project Note / Custom Watermark Text
            OutlinedTextField(
                value = projectNote,
                onValueChange = { projectNote = it },
                label = { Text("Note (mis. Captured by GPS Map Camera)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_note_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val parsedLat = latText.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: location.latitude
                    val parsedLon = lonText.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: location.longitude
                    val parsedAlt = altText.toDoubleOrNull() ?: location.altitudeMeters
                    onSaveLocation(
                        isManual,
                        locationSource,
                        parsedLat,
                        parsedLon,
                        parsedAlt,
                        plusCode,
                        street,
                        subLocality,
                        locality,
                        city,
                        province,
                        postalCode,
                        country,
                        showCountryFlag,
                        projectNote,
                        customFullAddressOverride
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalAmber,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_location_button")
            ) {
                Text(
                    text = "Simpan Lokasi ke Stamp & Database",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (showInteractiveMapPicker) {
        val startLat = latText.toDoubleOrNull()?.coerceIn(-85.0, 85.0) ?: location.latitude
        val startLon = lonText.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: location.longitude
        InteractiveMapPickerDialog(
            initialLatitude = startLat,
            initialLongitude = startLon,
            initialAddress = location.fullAddress,
            mapTileFetcher = mapTileFetcher,
            onReverseGeocodeRequest = onReverseGeocodeRequest,
            onSearchPlacesRequest = onSearchPlacesRequest,
            onRequestMyGpsLocation = onRequestMyGpsLocation,
            onDismissCancel = {
                // Pressing "Batal" discards unconfirmed map movements
                showInteractiveMapPicker = false
            },
            onConfirmLocation = { picked ->
                // Pressing "Pilih Lokasi (OK)" updates the form with confirmed coordinates & address
                isManual = true
                locationSource = LocationSource.MANUAL_MAP
                latText = String.format(java.util.Locale.US, "%.6f", picked.latitude)
                lonText = String.format(java.util.Locale.US, "%.6f", picked.longitude)
                plusCode = picked.plusCode
                val parts = picked.geocodedParts
                if (parts != null) {
                    street = parts.street.ifBlank { street }
                    subLocality = parts.subLocality
                    locality = parts.locality
                    city = parts.city
                    province = parts.province
                    postalCode = parts.postalCode
                    country = parts.country.ifBlank { country }
                    customFullAddressOverride = ""
                } else {
                    customFullAddressOverride = picked.addressText
                }
                showInteractiveMapPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StampStyleModalSheet(
    styleConfig: StampStyleConfig,
    onDismiss: () -> Unit,
    onTemplateSelected: (StampTemplate) -> Unit,
    onMiniMapStyleSelected: (MiniMapStyle) -> Unit,
    onPositionSelected: (StampPosition) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onVisibilityChanged: (
        showMiniMap: Boolean,
        showBrandBadgeTab: Boolean,
        showCompass: Boolean,
        showAltitudeAndAccuracy: Boolean,
        showProjectNote: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TacticalSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Preset Template & Gaya Peta",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Sesuaikan 100% dengan Gambar Referensi 1 & 2",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Pilih Template Stamp:",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            StampTemplate.entries.forEach { tmpl ->
                val selected = styleConfig.template == tmpl
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) TacticalSurfaceVariant else ObsidianBackground)
                        .border(
                            1.dp,
                            if (selected) SignalAmber else BorderSubtle,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onTemplateSelected(tmpl) }
                        .padding(12.dp)
                        .testTag("template_option_${tmpl.name}")
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onTemplateSelected(tmpl) },
                        colors = RadioButtonDefaults.colors(selectedColor = SignalAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = tmpl.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) SignalAmber else Color.White
                        )
                        Text(
                            text = tmpl.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mini-Map Style (Auto by template, Satellite, or Light Street Map)
            Text(
                text = "Gaya Peta Mini di Sebelah Kiri:",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniMapStyle.entries.forEach { mapStyle ->
                    val selected = styleConfig.miniMapStyle == mapStyle
                    FilterChip(
                        selected = selected,
                        onClick = { onMiniMapStyleSelected(mapStyle) },
                        label = { Text(mapStyle.label, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalAmber,
                            selectedLabelColor = ObsidianBackground
                        ),
                        modifier = Modifier.testTag("map_style_chip_${mapStyle.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Position (Bottom / Top)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Posisi Stamp pada Foto:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StampPosition.entries.forEach { pos ->
                        val selected = styleConfig.position == pos
                        FilterChip(
                            selected = selected,
                            onClick = { onPositionSelected(pos) },
                            label = { Text(pos.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SignalAmber,
                                selectedLabelColor = ObsidianBackground
                            ),
                            modifier = Modifier.testTag("position_chip_${pos.name}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Background Opacity Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Transparansi Latar Stamp:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "${(styleConfig.backgroundOpacity * 100).roundToInt()}%",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = SignalAmber
                )
            }
            Slider(
                value = styleConfig.backgroundOpacity,
                onValueChange = onOpacityChanged,
                valueRange = 0.40f..0.95f,
                colors = SliderDefaults.colors(
                    thumbColor = SignalAmber,
                    activeTrackColor = SignalAmber
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Element Toggles
            StyleToggleRow(
                label = "Tampilkan Peta Mini di Sisi Kiri",
                checked = styleConfig.showMiniMap,
                onCheckedChange = {
                    onVisibilityChanged(
                        it,
                        styleConfig.showBrandBadgeTab,
                        styleConfig.showCompass,
                        styleConfig.showAltitudeAndAccuracy,
                        styleConfig.showProjectNote
                    )
                }
            )
            StyleToggleRow(
                label = "Tampilkan Tab Badge 'GPS Map Camera' (Template 1)",
                checked = styleConfig.showBrandBadgeTab,
                onCheckedChange = {
                    onVisibilityChanged(
                        styleConfig.showMiniMap,
                        it,
                        styleConfig.showCompass,
                        styleConfig.showAltitudeAndAccuracy,
                        styleConfig.showProjectNote
                    )
                }
            )
            StyleToggleRow(
                label = "Tampilkan Baris Note / Catatan",
                checked = styleConfig.showProjectNote,
                onCheckedChange = {
                    onVisibilityChanged(
                        styleConfig.showMiniMap,
                        styleConfig.showBrandBadgeTab,
                        styleConfig.showCompass,
                        styleConfig.showAltitudeAndAccuracy,
                        it
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalAmber,
                    contentColor = ObsidianBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Selesai", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StyleToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBackground,
                checkedTrackColor = GpsEmerald
            )
        )
    }
}
