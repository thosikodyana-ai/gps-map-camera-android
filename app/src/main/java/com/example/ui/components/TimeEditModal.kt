package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DateFormatOption
import com.example.model.TimeConfig
import com.example.model.TimeZoneOption
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeEditModalSheet(
    timeConfig: TimeConfig,
    onDismiss: () -> Unit,
    onToggleManualMode: (Boolean) -> Unit,
    onDateSelected: (year: Int, monthZeroBased: Int, dayOfMonth: Int) -> Unit,
    onTimeSelected: (hourOfDay: Int, minute: Int, second: Int) -> Unit,
    onTimeZoneSelected: (TimeZoneOption) -> Unit,
    onDateFormatSelected: (DateFormatOption) -> Unit,
    onResetToNow: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val tz = TimeZone.getTimeZone(timeConfig.timeZoneOption.zoneId)
    val calendar = remember(timeConfig.effectiveEpochMillis, timeConfig.timeZoneOption) {
        Calendar.getInstance(tz).apply {
            timeInMillis = timeConfig.effectiveEpochMillis
        }
    }
    var customSeconds by remember(timeConfig.effectiveEpochMillis) {
        mutableIntStateOf(calendar.get(Calendar.SECOND))
    }

    val currentDateText = remember(timeConfig.effectiveEpochMillis, timeConfig.timeZoneOption) {
        SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).apply {
            timeZone = tz
        }.format(Date(timeConfig.effectiveEpochMillis))
    }
    val currentTimeText = remember(timeConfig.effectiveEpochMillis, timeConfig.timeZoneOption) {
        SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = tz
        }.format(Date(timeConfig.effectiveEpochMillis))
    }

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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pengaturan Tanggal & Jam Stamp",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Sinkron otomatis ke Canvas Watermark & EXIF JPEG",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_time_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Watermark & EXIF Preview Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBackground)
                    .border(
                        1.dp,
                        if (timeConfig.isManualOverride) SignalAmber else GpsEmerald,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (timeConfig.isManualOverride) "MODE MANUAL OVERRIDE" else "MODE AUTO (REAL-TIME)",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (timeConfig.isManualOverride) SignalAmber else GpsEmerald
                    )
                    Text(
                        text = "EXIF: ${timeConfig.formatExifDateTime()}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = CompassCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = timeConfig.formatDisplayTimestamp(),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    modifier = Modifier.testTag("preview_formatted_timestamp")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Switch Auto / Manual Override
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TacticalSurfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manual Date & Time Override",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (timeConfig.isManualOverride) {
                            "Menggunakan tanggal & jam kustom pilihan Anda"
                        } else {
                            "Mengikuti jam sistem & jaringan secara real-time"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = timeConfig.isManualOverride,
                    onCheckedChange = onToggleManualMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBackground,
                        checkedTrackColor = SignalAmber
                    ),
                    modifier = Modifier.testTag("manual_time_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DatePicker & TimePicker Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showDatePickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_date_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Pilih Tanggal", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = currentDateText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showTimePickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_time_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = SignalAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Pilih Jam", fontSize = 10.sp, color = TextSecondary)
                        Text(
                            text = currentTimeText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Fine-tune Seconds slider when in Manual mode
            if (timeConfig.isManualOverride) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Detik (:ss): ${String.format(Locale.US, "%02d", customSeconds)}s",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.width(105.dp)
                    )
                    Slider(
                        value = customSeconds.toFloat(),
                        onValueChange = { newSec ->
                            val secInt = newSec.toInt().coerceIn(0, 59)
                            customSeconds = secInt
                            onTimeSelected(
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                secInt
                            )
                        },
                        valueRange = 0f..59f,
                        colors = SliderDefaults.colors(
                            thumbColor = SignalAmber,
                            activeTrackColor = SignalAmber
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reset to Current Time Button
            OutlinedButton(
                onClick = onResetToNow,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_time_now_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = GpsEmerald
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset ke Jam Sekarang (Real-Time)",
                    color = GpsEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Timezone Selection (WIB, WITA, WIT, UTC)
            Text(
                text = "Zona Waktu (Timezone)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TimeZoneOption.entries.forEach { tzOption ->
                    val selected = timeConfig.timeZoneOption == tzOption
                    FilterChip(
                        selected = selected,
                        onClick = { onTimeZoneSelected(tzOption) },
                        label = {
                            Text(
                                text = tzOption.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalAmber,
                            selectedLabelColor = ObsidianBackground
                        ),
                        modifier = Modifier.testTag("tz_chip_${tzOption.shortLabel}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Format Selection
            Text(
                text = "Format Tampilan Timestamp",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            DateFormatOption.entries.forEach { fmt ->
                val selected = timeConfig.formatOption == fmt
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) TacticalSurfaceVariant else Color.Transparent)
                        .clickable { onDateFormatSelected(fmt) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("format_option_${fmt.name}")
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onDateFormatSelected(fmt) },
                        colors = RadioButtonDefaults.colors(selectedColor = SignalAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = fmt.pattern,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) SignalAmber else Color.White
                        )
                        Text(
                            text = "Contoh: ${fmt.exampleLabel} ${timeConfig.timeZoneOption.shortLabel}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

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
                    .testTag("apply_time_button")
            ) {
                Text(
                    text = "Terapkan Pengaturan Waktu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = timeConfig.effectiveEpochMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcMillis ->
                            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedUtcMillis
                            }
                            onDateSelected(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("Pilih Tanggal", color = SignalAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = TacticalSurface,
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Pilih Jam & Menit (${timeConfig.timeZoneOption.shortLabel})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showTimePickerDialog = false }) {
                            Text("Batal", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onTimeSelected(
                                    timePickerState.hour,
                                    timePickerState.minute,
                                    customSeconds
                                )
                                showTimePickerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignalAmber,
                                contentColor = ObsidianBackground
                            )
                        ) {
                            Text("Simpan Jam", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
