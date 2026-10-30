package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.StampedPhotoEntity
import com.example.model.LocationMetadata
import com.example.model.TimeConfig
import com.example.storage.ExifInspectionReport
import com.example.storage.MediaStoreExifSaver
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun StampedPreviewModal(
    stampedBitmap: Bitmap,
    isRendering: Boolean,
    isSaving: Boolean,
    timeConfig: TimeConfig,
    location: LocationMetadata,
    onRetakeOrDiscard: () -> Unit,
    onSaveToGallery: () -> Unit,
    onOpenTimeEdit: () -> Unit,
    onOpenLocationEdit: () -> Unit,
    onOpenStyleEdit: () -> Unit
) {
    Dialog(
        onDismissRequest = onRetakeOrDiscard,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSaving
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag("stamped_preview_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Pratinjau Hasil Stamp & EXIF",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Anda masih dapat mengedit jam, lokasi peta, atau template",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onRetakeOrDiscard,
                    enabled = !isSaving
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup Pratinjau", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = onOpenTimeEdit,
                    label = { Text("Edit Waktu", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = SignalAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TacticalSurface,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.testTag("preview_edit_time_chip")
                )
                AssistChip(
                    onClick = onOpenLocationEdit,
                    label = { Text("Lokasi / Peta", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.EditLocationAlt,
                            contentDescription = null,
                            tint = CompassCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TacticalSurface,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.testTag("preview_edit_location_chip")
                )
                AssistChip(
                    onClick = onOpenStyleEdit,
                    label = { Text("Gaya Stamp", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = GpsEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TacticalSurface,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.testTag("preview_edit_style_chip")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val cachedPreviewImage = remember(stampedBitmap) {
                stampedBitmap.asImageBitmap()
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TacticalSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Image(
                    bitmap = cachedPreviewImage,
                    contentDescription = "Hasil Foto dengan GPS Timestamp",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )

                if (isRendering) {
                    Surface(
                        color = ObsidianBackground.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = SignalAmber,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Memperbarui Stamp...", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TacticalSurface)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "SUMBER LOKASI: ${location.locationSource.uppercase()}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = GpsEmerald
                    )
                    Text(
                        text = "DCIM/GPSMapCamera",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = SignalAmber
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "DateTimeOriginal: ${timeConfig.formatExifDateTime()} (${timeConfig.timeZoneOption.exifOffset})",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = Color.White
                )
                Text(
                    text = "GPSLat/Lon: ${location.decimalCoordinatesText} • PlusCode: ${location.plusCode}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = CompassCyan
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onRetakeOrDiscard,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("preview_retake_button")
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ulangi", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onSaveToGallery,
                    enabled = !isSaving && !isRendering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalAmber,
                        contentColor = ObsidianBackground
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.6f)
                        .height(52.dp)
                        .testTag("preview_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = ObsidianBackground,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menyimpan...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan ke Galeri", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoHistoryModalSheet(
    photos: List<StampedPhotoEntity>,
    selectedExifReport: ExifInspectionReport?,
    onDismiss: () -> Unit,
    onInspectExif: (StampedPhotoEntity) -> Unit,
    onClearExifReport: () -> Unit,
    onDeletePhoto: (StampedPhotoEntity) -> Unit
) {
    val context = LocalContext.current
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Galeri Riwayat Foto (${photos.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Tersimpan di DCIM/GPSMapCamera & Database Room",
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

            if (selectedExifReport != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianBackground),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CompassCyan, RoundedCornerShape(12.dp))
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "HASIL INSPEKSI METADATA EXIF JPEG",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CompassCyan
                            )
                            IconButton(
                                onClick = onClearExifReport,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Tutup EXIF",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "DateTimeOriginal: ${selectedExifReport.dateTimeOriginal} (${selectedExifReport.offsetTimeOriginal})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = SignalAmber
                        )
                        Text(
                            text = "GPSLatitude: ${selectedExifReport.gpsLatitude} | GPSLongitude: ${selectedExifReport.gpsLongitude}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = GpsEmerald
                        )
                        Text(
                            text = "GPSAltitude: ${selectedExifReport.gpsAltitude} | Dimensi: ${selectedExifReport.dimensions}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }

            if (photos.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum Ada Riwayat Foto",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ambil foto dari kamera atau impor dari galeri untuk menyimpan foto berpenanda GPS & Timestamp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                ) {
                    items(photos, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TacticalSurfaceVariant),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AsyncImage(
                                        model = File(item.localFilePath),
                                        contentDescription = item.displayTimestamp,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ObsidianBackground)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (item.isManualTime) SignalAmber else GpsEmerald,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isManualTime) "MANUAL TIME" else "AUTO TIME",
                                                    color = ObsidianBackground,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = CompassCyan.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = item.locationSource.uppercase(),
                                                    color = CompassCyan,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.displayTimestamp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.fullAddress,
                                            fontSize = 11.sp,
                                            color = Color(0xFFE2E8F0),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.decimalCoordinates} (${item.plusCode})",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = CompassCyan
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { onInspectExif(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = CompassCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cek EXIF", fontSize = 11.sp, color = CompassCyan)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            MediaStoreExifSaver.sharePhotoFile(
                                                context = context,
                                                localFilePath = item.localFilePath,
                                                caption = "${item.displayTimestamp}\n${item.fullAddress}\nGPS: ${item.decimalCoordinates}"
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = null,
                                            tint = SignalAmber,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bagikan", fontSize = 11.sp, color = SignalAmber)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onDeletePhoto(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Foto",
                                            tint = AlertCoral
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
