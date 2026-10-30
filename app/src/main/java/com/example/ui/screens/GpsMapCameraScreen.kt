package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CameraAspectRatio
import com.example.model.FlashModeOption
import com.example.model.LocationSource
import com.example.model.StampPosition
import com.example.model.StampTemplate
import com.example.ui.components.InteractiveMapPickerDialog
import com.example.ui.components.LiveStampOverlayCard
import com.example.ui.components.LocationEditModalSheet
import com.example.ui.components.PhotoHistoryModalSheet
import com.example.ui.components.StampStyleModalSheet
import com.example.ui.components.StampedPreviewModal
import com.example.ui.components.TimeEditModalSheet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CompassCyan
import com.example.ui.theme.GpsEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.TacticalHudOverlay
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CameraStampViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GpsMapCameraScreen(
    viewModel: CameraStampViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val timeConfig by viewModel.timeConfig.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val styleConfig by viewModel.styleConfig.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val isFrontCamera by viewModel.isFrontCamera.collectAsStateWithLifecycle()
    val miniMapBitmap by viewModel.miniMapBitmap.collectAsStateWithLifecycle()
    val previewStampedBitmap by viewModel.previewStampedBitmap.collectAsStateWithLifecycle()
    val previewRotationDegrees by viewModel.previewRotationDegrees.collectAsStateWithLifecycle()
    val isRenderingPreview by viewModel.isRenderingPreview.collectAsStateWithLifecycle()
    val isSavingPhoto by viewModel.isSavingPhoto.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val historyPhotos by viewModel.historyPhotos.collectAsStateWithLifecycle()
    val selectedPhotoExif by viewModel.selectedPhotoExif.collectAsStateWithLifecycle()

    var showTimeModal by remember { mutableStateOf(false) }
    var showLocationModal by remember { mutableStateOf(false) }
    var showDirectMapPickerModal by remember { mutableStateOf(false) }
    var showStyleModal by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var showShutterFlash by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            viewModel.startLocationTrackingIfPermitted()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocationTrackingIfPermitted()
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToastMessage()
        }
    }

    // Zero-permission Android Photo Picker for importing image from Gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onGalleryImagePicked(uri)
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(hasCameraPermission, isFrontCamera, aspectRatio) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val targetRatio = if (aspectRatio == CameraAspectRatio.RATIO_4_3) {
                    AspectRatio.RATIO_4_3
                } else {
                    AspectRatio.RATIO_16_9
                }

                val preview = Preview.Builder()
                    .setTargetAspectRatio(targetRatio)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(targetRatio)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                imageCapture = capture

                val selector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(flashMode, imageCapture) {
        imageCapture?.flashMode = when (flashMode) {
            FlashModeOption.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
        }
    }

    BackHandler(
        enabled = showTimeModal || showLocationModal || showDirectMapPickerModal ||
            showStyleModal || showHistoryModal
    ) {
        when {
            showDirectMapPickerModal -> showDirectMapPickerModal = false
            showTimeModal -> showTimeModal = false
            showLocationModal -> showLocationModal = false
            showStyleModal -> showStyleModal = false
            showHistoryModal -> showHistoryModal = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Top Tactical Camera Bar
            TopCameraControlBar(
                aspectRatio = aspectRatio,
                flashMode = flashMode,
                isFrontCamera = isFrontCamera,
                isGpsLocked = locationState.isGpsLocked,
                isManualLocation = locationState.isManualLocation,
                locationSource = locationState.locationSource,
                accuracyMeters = locationState.accuracyMeters,
                onToggleAspectRatio = viewModel::toggleAspectRatio,
                onCycleFlash = viewModel::cycleFlashMode,
                onSwitchCamera = viewModel::toggleCameraFacing,
                onLocationPillClick = { showLocationModal = true }
            )

            // 2. Center Camera Viewfinder + Live Stamp Overlay + Floating "Edit Waktu" & "Peta" Buttons
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF070A0E))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            ) {
                val targetRatioFloat = if (aspectRatio == CameraAspectRatio.RATIO_4_3) {
                    3f / 4f
                } else {
                    9f / 16f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(targetRatioFloat, matchHeightConstraintsFirst = false)
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("camerax_preview_view")
                        )
                    } else {
                        CameraPermissionFallbackOverlay(
                            onRequestPermissions = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onOpenInteractiveMap = { showDirectMapPickerModal = true }
                        )
                    }

                    // Live Stamp Overlay positioned at TOP or BOTTOM of viewfinder
                    Box(
                        modifier = Modifier
                            .align(
                                if (styleConfig.position == StampPosition.TOP) {
                                    Alignment.TopCenter
                                } else {
                                    Alignment.BottomCenter
                                }
                            )
                            .padding(8.dp)
                    ) {
                        LiveStampOverlayCard(
                            timeConfig = timeConfig,
                            location = locationState,
                            styleConfig = styleConfig,
                            miniMapBitmap = miniMapBitmap,
                            onTimeClick = { showTimeModal = true },
                            onLocationClick = { showLocationModal = true }
                        )
                    }

                    // Floating Quick Actions Bar
                    val floatingControlsAlignment = if (styleConfig.position == StampPosition.BOTTOM) {
                        Alignment.TopEnd
                    } else {
                        Alignment.BottomEnd
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(floatingControlsAlignment)
                            .padding(12.dp)
                    ) {
                        // Primary Floating Button: "Edit Waktu" (Icon Jam) from PRD
                        ExtendedFloatingActionButton(
                            onClick = { showTimeModal = true },
                            containerColor = if (timeConfig.isManualOverride) SignalAmber else TacticalHudOverlay,
                            contentColor = if (timeConfig.isManualOverride) ObsidianBackground else Color.White,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(42.dp)
                                .border(1.dp, SignalAmber, RoundedCornerShape(50))
                                .testTag("edit_time_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = stringResource(R.string.edit_time_button),
                                tint = if (timeConfig.isManualOverride) ObsidianBackground else SignalAmber,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (timeConfig.isManualOverride) {
                                    "Edit Waktu • MANUAL (${timeConfig.timeZoneOption.shortLabel})"
                                } else {
                                    "Edit Waktu • AUTO (${timeConfig.timeZoneOption.shortLabel})"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        // Secondary Quick Pill Row: Pilih Peta | Form Lokasi | Template 1/2 | Foto Uji
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuickHudChip(
                                icon = Icons.Default.Map,
                                label = "Pilih Peta",
                                accentColor = CompassCyan,
                                onClick = { showDirectMapPickerModal = true },
                                testTag = "open_direct_map_picker_button"
                            )
                            QuickHudChip(
                                icon = Icons.Default.EditLocationAlt,
                                label = "Lokasi",
                                accentColor = CompassCyan,
                                onClick = { showLocationModal = true },
                                testTag = "open_location_modal_button"
                            )
                            QuickHudChip(
                                icon = Icons.Default.Palette,
                                label = when (styleConfig.template) {
                                    StampTemplate.CLASSIC -> "Tmpl 1"
                                    StampTemplate.MODERN -> "Tmpl 2"
                                    StampTemplate.MINIMALIST -> "Min"
                                },
                                accentColor = GpsEmerald,
                                onClick = { showStyleModal = true },
                                testTag = "open_style_modal_button"
                            )
                            QuickHudChip(
                                icon = Icons.Default.Landscape,
                                label = "Foto Uji",
                                accentColor = SignalAmber,
                                onClick = { viewModel.captureSampleFieldScene() },
                                testTag = "sample_field_photo_button"
                            )
                        }
                    }

                    // Shutter White Flash feedback
                    this@Column.AnimatedVisibility(
                        visible = showShutterFlash,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.75f))
                        )
                    }
                }
            }

            // 3. Bottom Camera Dock: Gallery Picker | Shutter Capture | Saved History Gallery
            BottomCameraDock(
                historyCount = historyPhotos.size,
                onPickFromGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCapturePhoto = {
                    coroutineScope.launch {
                        showShutterFlash = true
                        delay(110)
                        showShutterFlash = false
                    }
                    captureCameraFrameOrFallback(
                        context = context,
                        imageCapture = imageCapture,
                        previewView = previewView,
                        isFrontCamera = isFrontCamera,
                        onBitmapReady = { bmp ->
                            viewModel.onRawPhotoCaptured(bmp)
                        },
                        onFallbackSample = {
                            viewModel.captureSampleFieldScene()
                        }
                    )
                },
                onOpenHistory = { showHistoryModal = true }
            )
        }
    }

    // --- Modals & Dialogs ---
    if (showTimeModal) {
        TimeEditModalSheet(
            timeConfig = timeConfig,
            onDismiss = { showTimeModal = false },
            onToggleManualMode = viewModel::setTimeManualMode,
            onDateSelected = viewModel::updateCustomDate,
            onTimeSelected = viewModel::updateCustomTime,
            onTimeZoneSelected = viewModel::setTimeZoneOption,
            onDateFormatSelected = viewModel::setDateFormatOption,
            onResetToNow = viewModel::resetTimeToNow
        )
    }

    if (showLocationModal) {
        LocationEditModalSheet(
            location = locationState,
            mapTileFetcher = viewModel.mapTileFetcher,
            onReverseGeocodeRequest = viewModel::reverseGeocodeCoordinates,
            onSearchPlacesRequest = viewModel::searchPlacesOnMap,
            onRequestMyGpsLocation = viewModel::requestOneShotMyGpsLocation,
            onDismiss = { showLocationModal = false },
            onSaveLocation = viewModel::setManualLocationData,
            onTriggerReverseGeocode = viewModel::triggerReverseGeocodeForCurrentCoordinates
        )
    }

    if (showDirectMapPickerModal) {
        InteractiveMapPickerDialog(
            initialLatitude = locationState.latitude,
            initialLongitude = locationState.longitude,
            initialAddress = locationState.fullAddress,
            mapTileFetcher = viewModel.mapTileFetcher,
            onReverseGeocodeRequest = viewModel::reverseGeocodeCoordinates,
            onSearchPlacesRequest = viewModel::searchPlacesOnMap,
            onRequestMyGpsLocation = viewModel::requestOneShotMyGpsLocation,
            onDismissCancel = { showDirectMapPickerModal = false },
            onConfirmLocation = { picked ->
                val parts = picked.geocodedParts
                viewModel.setManualLocationData(
                    isManual = true,
                    locationSource = LocationSource.MANUAL_MAP,
                    latitude = picked.latitude,
                    longitude = picked.longitude,
                    altitudeMeters = locationState.altitudeMeters,
                    plusCode = picked.plusCode,
                    street = parts?.street?.ifBlank { locationState.street } ?: locationState.street,
                    subLocality = parts?.subLocality ?: locationState.subLocality,
                    locality = parts?.locality ?: locationState.locality,
                    city = parts?.city ?: locationState.city,
                    province = parts?.province ?: locationState.province,
                    postalCode = parts?.postalCode ?: locationState.postalCode,
                    country = parts?.country?.ifBlank { locationState.country } ?: locationState.country,
                    showCountryFlag = locationState.showCountryFlag,
                    projectNote = locationState.projectNote,
                    customFullAddressOverride = if (parts == null) picked.addressText else ""
                )
                showDirectMapPickerModal = false
            }
        )
    }

    if (showStyleModal) {
        StampStyleModalSheet(
            styleConfig = styleConfig,
            onDismiss = { showStyleModal = false },
            onTemplateSelected = viewModel::setStampTemplate,
            onMiniMapStyleSelected = viewModel::setMiniMapStyle,
            onPositionSelected = viewModel::setStampPosition,
            onOpacityChanged = viewModel::setStampOpacity,
            onVisibilityChanged = viewModel::updateStampVisibility
        )
    }

    if (showHistoryModal) {
        PhotoHistoryModalSheet(
            photos = historyPhotos,
            selectedExifReport = selectedPhotoExif,
            onDismiss = {
                viewModel.clearSelectedPhotoExif()
                showHistoryModal = false
            },
            onInspectExif = viewModel::inspectPhotoExif,
            onClearExifReport = viewModel::clearSelectedPhotoExif,
            onDeletePhoto = viewModel::deleteHistoryPhoto
        )
    }

    previewStampedBitmap?.let { stampedBmp ->
        StampedPreviewModal(
            stampedBitmap = stampedBmp,
            rotationDegrees = previewRotationDegrees,
            isRendering = isRenderingPreview,
            isSaving = isSavingPhoto,
            timeConfig = timeConfig,
            location = locationState,
            onRetakeOrDiscard = viewModel::discardPreview,
            onSaveToGallery = { viewModel.saveCurrentPreviewToGallery() },
            onRotatePhoto90 = viewModel::rotatePreviewClockwise,
            onOpenTimeEdit = { showTimeModal = true },
            onOpenLocationEdit = { showLocationModal = true },
            onOpenStyleEdit = { showStyleModal = true }
        )
    }
}

@Composable
private fun TopCameraControlBar(
    aspectRatio: CameraAspectRatio,
    flashMode: FlashModeOption,
    isFrontCamera: Boolean,
    isGpsLocked: Boolean,
    isManualLocation: Boolean,
    locationSource: String,
    accuracyMeters: Float,
    onToggleAspectRatio: () -> Unit,
    onCycleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLocationPillClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Left: GPS / Manual Map Status Pill
        Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .border(
                    1.dp,
                    if (isManualLocation) SignalAmber else GpsEmerald.copy(alpha = 0.7f),
                    RoundedCornerShape(50)
                )
                .clickable(onClick = onLocationPillClick)
                .testTag("gps_status_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (locationSource == LocationSource.MANUAL_MAP) {
                        Icons.Default.Map
                    } else {
                        Icons.Default.GpsFixed
                    },
                    contentDescription = null,
                    tint = if (isManualLocation) SignalAmber else GpsEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        locationSource == LocationSource.MANUAL_MAP -> "MAP MANUAL"
                        isManualLocation -> "LOC MANUAL"
                        isGpsLocked -> String.format(Locale.US, "GPS ±%.0fm", accuracyMeters)
                        else -> "GPS READY"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }

        // Right: Aspect Ratio (4:3 / 16:9), Flash Mode, Camera Lens Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = TacticalSurface,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, BorderSubtle, RoundedCornerShape(50))
                    .clickable(onClick = onToggleAspectRatio)
                    .testTag("aspect_ratio_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = stringResource(R.string.toggle_aspect_ratio_desc),
                        tint = SignalAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = aspectRatio.label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

            IconButton(
                onClick = onCycleFlash,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TacticalSurface)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .testTag("flash_mode_button")
            ) {
                val flashIcon = when (flashMode) {
                    FlashModeOption.OFF -> Icons.Default.FlashOff
                    FlashModeOption.AUTO -> Icons.Default.FlashAuto
                    FlashModeOption.ON -> Icons.Default.FlashOn
                }
                Icon(
                    imageVector = flashIcon,
                    contentDescription = stringResource(R.string.toggle_flash_desc),
                    tint = if (flashMode == FlashModeOption.OFF) TextSecondary else SignalAmber
                )
            }

            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TacticalSurface)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .testTag("switch_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.switch_camera_desc),
                    tint = if (isFrontCamera) CompassCyan else Color.White
                )
            }
        }
    }
}

@Composable
private fun QuickHudChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = TacticalHudOverlay,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BottomCameraDock(
    historyCount: Int,
    onPickFromGallery: () -> Unit,
    onCapturePhoto: () -> Unit,
    onOpenHistory: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        FilledTonalButton(
            onClick = onPickFromGallery,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = TacticalSurface,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(52.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .testTag("pick_gallery_button")
        ) {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = stringResource(R.string.pick_gallery_button),
                tint = CompassCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Impor Galeri",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(3.dp, SignalAmber, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(SignalAmber)
                .clickable(onClick = onCapturePhoto)
                .testTag("shutter_capture_button")
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = stringResource(R.string.capture_photo_desc),
                tint = ObsidianBackground,
                modifier = Modifier.size(32.dp)
            )
        }

        FilledTonalButton(
            onClick = onOpenHistory,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = TacticalSurface,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(52.dp)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .testTag("open_history_button")
        ) {
            BadgedBox(
                badge = {
                    if (historyCount > 0) {
                        Badge(
                            containerColor = GpsEmerald,
                            contentColor = ObsidianBackground
                        ) {
                            Text(historyCount.toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(R.string.history_gallery_button),
                    tint = SignalAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Riwayat",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CameraPermissionFallbackOverlay(
    onRequestPermissions: () -> Unit,
    onOpenInteractiveMap: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1621))
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(TacticalSurface.copy(alpha = 0.9f))
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = SignalAmber,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Kamera & Pemilihan Lokasi Peta",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Anda tetap dapat memilih lokasi secara manual melalui Peta Interaktif tanpa izin GPS, atau gunakan tombol Impor Galeri / Foto Uji.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onOpenInteractiveMap,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CompassCyan,
                        contentColor = ObsidianBackground
                    ),
                    modifier = Modifier.testTag("fallback_open_map_button")
                ) {
                    Text("Pilih di Peta", fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SignalAmber,
                        contentColor = ObsidianBackground
                    ),
                    modifier = Modifier.testTag("grant_permissions_button")
                ) {
                    Text("Izin Kamera", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun captureCameraFrameOrFallback(
    context: Context,
    imageCapture: ImageCapture?,
    previewView: PreviewView,
    isFrontCamera: Boolean,
    onBitmapReady: (Bitmap) -> Unit,
    onFallbackSample: () -> Unit
) {
    val previewSnapshot = runCatching { previewView.bitmap }.getOrNull()

    if (imageCapture == null) {
        if (previewSnapshot != null) {
            onBitmapReady(previewSnapshot)
        } else {
            onFallbackSample()
        }
        return
    }

    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val capturedBmp = runCatching {
                    val raw = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    val matrix = Matrix().apply {
                        if (rotation != 0) {
                            postRotate(rotation.toFloat())
                        }
                        if (isFrontCamera) {
                            postScale(-1f, 1f)
                        }
                    }
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                }.getOrNull()
                image.close()

                when {
                    capturedBmp != null -> onBitmapReady(capturedBmp)
                    previewSnapshot != null -> onBitmapReady(previewSnapshot)
                    else -> onFallbackSample()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                if (previewSnapshot != null) {
                    onBitmapReady(previewSnapshot)
                } else {
                    onFallbackSample()
                }
            }
        }
    )
}
