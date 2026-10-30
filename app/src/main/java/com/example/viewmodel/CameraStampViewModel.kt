package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PhotoHistoryRepository
import com.example.data.local.SavedLocationProfileEntity
import com.example.data.local.StampedPhotoEntity
import com.example.model.CameraAspectRatio
import com.example.model.DateFormatOption
import com.example.model.FlashModeOption
import com.example.model.LocationMetadata
import com.example.model.LocationSource
import com.example.model.MiniMapStyle
import com.example.model.PlusCodeGenerator
import com.example.model.StampPosition
import com.example.model.StampStyleConfig
import com.example.model.StampTemplate
import com.example.model.TimeConfig
import com.example.model.TimeZoneOption
import com.example.sensor.GeocodedAddressParts
import com.example.sensor.LocationAndCompassEngine
import com.example.stamp.BitmapStampRenderer
import com.example.stamp.MapTileFetcher
import com.example.storage.ExifInspectionReport
import com.example.storage.MediaStoreExifSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

class CameraStampViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = PhotoHistoryRepository(database.stampedPhotoDao())
    private val locationEngine = LocationAndCompassEngine(application)
    val mapTileFetcher = MapTileFetcher(application)

    val historyPhotos: StateFlow<List<StampedPhotoEntity>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _timeConfig = MutableStateFlow(TimeConfig())
    val timeConfig: StateFlow<TimeConfig> = _timeConfig.asStateFlow()

    private val _locationState = MutableStateFlow(LocationMetadata())
    val locationState: StateFlow<LocationMetadata> = _locationState.asStateFlow()

    private val _styleConfig = MutableStateFlow(StampStyleConfig())
    val styleConfig: StateFlow<StampStyleConfig> = _styleConfig.asStateFlow()

    private val _aspectRatio = MutableStateFlow(CameraAspectRatio.RATIO_4_3)
    val aspectRatio: StateFlow<CameraAspectRatio> = _aspectRatio.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashModeOption.OFF)
    val flashMode: StateFlow<FlashModeOption> = _flashMode.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _miniMapBitmap = MutableStateFlow<Bitmap?>(null)
    val miniMapBitmap: StateFlow<Bitmap?> = _miniMapBitmap.asStateFlow()

    private val _rawCaptureBitmap = MutableStateFlow<Bitmap?>(null)
    private val _previewStampedBitmap = MutableStateFlow<Bitmap?>(null)
    val previewStampedBitmap: StateFlow<Bitmap?> = _previewStampedBitmap.asStateFlow()

    private val _isRenderingPreview = MutableStateFlow(false)
    val isRenderingPreview: StateFlow<Boolean> = _isRenderingPreview.asStateFlow()

    private val _isSavingPhoto = MutableStateFlow(false)
    val isSavingPhoto: StateFlow<Boolean> = _isSavingPhoto.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _selectedPhotoExif = MutableStateFlow<ExifInspectionReport?>(null)
    val selectedPhotoExif: StateFlow<ExifInspectionReport?> = _selectedPhotoExif.asStateFlow()

    private var locationCollectionJob: Job? = null
    private var lastMapLat: Double = Double.NaN
    private var lastMapLon: Double = Double.NaN
    private var lastMapSatellite: Boolean? = null

    init {
        loadSavedLocationProfileFromDatabase()
        startLiveClockTicker()
        startCompassAndMagneticObserver()
        refreshMiniMap(force = true)
    }

    private fun loadSavedLocationProfileFromDatabase() {
        viewModelScope.launch {
            val savedProfile = repository.getSavedLocationProfileOnce()
            if (savedProfile != null) {
                _locationState.update { current ->
                    current.copy(
                        isManualLocation = savedProfile.isManualLocation,
                        locationSource = savedProfile.locationSource,
                        latitude = savedProfile.latitude,
                        longitude = savedProfile.longitude,
                        altitudeMeters = savedProfile.altitudeMeters,
                        plusCode = savedProfile.plusCode,
                        street = savedProfile.street,
                        subLocality = savedProfile.subLocality,
                        locality = savedProfile.locality,
                        city = savedProfile.city,
                        province = savedProfile.province,
                        postalCode = savedProfile.postalCode,
                        country = savedProfile.country,
                        showCountryFlag = savedProfile.showCountryFlag,
                        projectNote = savedProfile.projectNote,
                        customFullAddressOverride = if (savedProfile.street.isBlank() && savedProfile.address.isNotBlank()) {
                            savedProfile.address
                        } else {
                            ""
                        }
                    )
                }
                refreshMiniMap(force = true)
            }
        }
    }

    private fun startLiveClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                if (!_timeConfig.value.isManualOverride) {
                    _timeConfig.update { it.copy(currentAutoMillis = System.currentTimeMillis()) }
                }
                delay(1000L)
            }
        }
    }

    private fun startCompassAndMagneticObserver() {
        viewModelScope.launch {
            locationEngine.observeCompassAndMagnetic().collect { reading ->
                _locationState.update {
                    it.copy(
                        azimuthDegrees = reading.azimuthDegrees,
                        magneticFieldUt = reading.magneticFieldUt
                    )
                }
            }
        }
    }

    fun startLocationTrackingIfPermitted() {
        if (locationCollectionJob?.isActive == true) return
        locationCollectionJob = viewModelScope.launch {
            locationEngine.observeLiveLocations(this).collect { loc ->
                onIncomingDeviceGpsLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null
                )
            }
        }
    }

    /**
     * Processes incoming device GPS telemetry ONLY when the user has NOT selected a manual location
     * ("manual_map" or "manual_form"). This guarantees manual map selections are never overwritten by GPS.
     */
    suspend fun onIncomingDeviceGpsLocation(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        accuracyMeters: Float?
    ) {
        val currentLoc = _locationState.value
        if (currentLoc.isManualLocation || currentLoc.locationSource != LocationSource.GPS) {
            return
        }

        val prevLat = currentLoc.latitude
        val prevLon = currentLoc.longitude
        val movedSignificantly = abs(latitude - prevLat) > 0.0002 || abs(longitude - prevLon) > 0.0002

        _locationState.update { current ->
            current.copy(
                isGpsLocked = true,
                locationSource = LocationSource.GPS,
                latitude = latitude,
                longitude = longitude,
                plusCode = PlusCodeGenerator.generateShortPlusCode(latitude, longitude),
                altitudeMeters = altitudeMeters ?: current.altitudeMeters,
                accuracyMeters = accuracyMeters ?: current.accuracyMeters
            )
        }

        if (movedSignificantly) {
            val geocoded = locationEngine.reverseGeocode(latitude, longitude)
            if (geocoded != null && !_locationState.value.isManualLocation &&
                _locationState.value.locationSource == LocationSource.GPS
            ) {
                _locationState.update { current ->
                    current.copy(
                        street = geocoded.street.ifBlank { current.street },
                        subLocality = geocoded.subLocality.ifBlank { current.subLocality },
                        locality = geocoded.locality.ifBlank { current.locality },
                        city = geocoded.city.ifBlank { current.city },
                        province = geocoded.province.ifBlank { current.province },
                        postalCode = geocoded.postalCode.ifBlank { current.postalCode },
                        country = geocoded.country.ifBlank { current.country }
                    )
                }
            }
            refreshMiniMap(force = false)
        }
    }

    suspend fun reverseGeocodeCoordinates(latitude: Double, longitude: Double): GeocodedAddressParts? {
        return locationEngine.reverseGeocode(latitude, longitude)
    }

    suspend fun requestOneShotMyGpsLocation(): Pair<Double, Double>? {
        val loc = locationEngine.getOneShotCurrentLocation()
        return if (loc != null) {
            Pair(loc.latitude, loc.longitude)
        } else {
            null
        }
    }

    fun refreshMiniMap(force: Boolean = false) {
        val loc = _locationState.value
        val isSatellite = _styleConfig.value.effectiveSatelliteMap
        if (!force && !lastMapLat.isNaN() &&
            abs(loc.latitude - lastMapLat) < 0.0003 &&
            abs(loc.longitude - lastMapLon) < 0.0003 &&
            lastMapSatellite == isSatellite
        ) {
            return
        }
        lastMapLat = loc.latitude
        lastMapLon = loc.longitude
        lastMapSatellite = isSatellite

        viewModelScope.launch {
            val mapBmp = mapTileFetcher.getMiniMapBitmap(
                latitude = loc.latitude,
                longitude = loc.longitude,
                isSatellite = isSatellite,
                streetHint = loc.street,
                sizePx = 280
            )
            _miniMapBitmap.value = mapBmp
            reRenderActivePreviewIfNeeded()
        }
    }

    // --- Camera Controls ---
    fun toggleCameraFacing() {
        _isFrontCamera.update { !it }
    }

    fun cycleFlashMode() {
        _flashMode.update { current ->
            when (current) {
                FlashModeOption.OFF -> FlashModeOption.AUTO
                FlashModeOption.AUTO -> FlashModeOption.ON
                FlashModeOption.ON -> FlashModeOption.OFF
            }
        }
    }

    fun toggleAspectRatio() {
        _aspectRatio.update { current ->
            if (current == CameraAspectRatio.RATIO_4_3) {
                CameraAspectRatio.RATIO_16_9
            } else {
                CameraAspectRatio.RATIO_4_3
            }
        }
    }

    // --- Custom Date & Time Override Controls ---
    fun setTimeManualMode(isManual: Boolean) {
        _timeConfig.update { current ->
            val now = System.currentTimeMillis()
            current.copy(
                isManualOverride = isManual,
                currentAutoMillis = now,
                customEpochMillis = if (isManual && !current.isManualOverride) current.effectiveEpochMillis else current.customEpochMillis
            )
        }
        reRenderActivePreviewIfNeeded()
    }

    fun updateCustomDate(year: Int, monthZeroBased: Int, dayOfMonth: Int) {
        val current = _timeConfig.value
        val tz = TimeZone.getTimeZone(current.timeZoneOption.zoneId)
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = current.effectiveEpochMillis
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }
        _timeConfig.update {
            it.copy(
                isManualOverride = true,
                customEpochMillis = cal.timeInMillis
            )
        }
        reRenderActivePreviewIfNeeded()
    }

    fun updateCustomTime(hourOfDay: Int, minute: Int, second: Int) {
        val current = _timeConfig.value
        val tz = TimeZone.getTimeZone(current.timeZoneOption.zoneId)
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = current.effectiveEpochMillis
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second.coerceIn(0, 59))
        }
        _timeConfig.update {
            it.copy(
                isManualOverride = true,
                customEpochMillis = cal.timeInMillis
            )
        }
        reRenderActivePreviewIfNeeded()
    }

    fun setTimeZoneOption(option: TimeZoneOption) {
        _timeConfig.update { it.copy(timeZoneOption = option) }
        reRenderActivePreviewIfNeeded()
    }

    fun setDateFormatOption(option: DateFormatOption) {
        _timeConfig.update { it.copy(formatOption = option) }
        reRenderActivePreviewIfNeeded()
    }

    fun resetTimeToNow() {
        val now = System.currentTimeMillis()
        _timeConfig.update {
            it.copy(
                isManualOverride = false,
                currentAutoMillis = now,
                customEpochMillis = now
            )
        }
        _toastMessage.value = "Waktu direset ke jam sistem real-time"
        reRenderActivePreviewIfNeeded()
    }

    // --- Location & Manual Map Picker Persistence ---
    fun setManualLocationData(
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
        customFullAddressOverride: String = ""
    ) {
        val resolvedPlusCode = plusCode.ifBlank {
            PlusCodeGenerator.generateShortPlusCode(latitude, longitude)
        }
        val resolvedSource = if (isManual) {
            if (locationSource == LocationSource.GPS) LocationSource.MANUAL_FORM else locationSource
        } else {
            LocationSource.GPS
        }

        _locationState.update { current ->
            current.copy(
                isManualLocation = isManual,
                locationSource = resolvedSource,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                plusCode = resolvedPlusCode,
                street = street,
                subLocality = subLocality,
                locality = locality,
                city = city,
                province = province,
                postalCode = postalCode,
                country = country,
                showCountryFlag = showCountryFlag,
                projectNote = projectNote,
                customFullAddressOverride = customFullAddressOverride
            )
        }

        val updatedState = _locationState.value
        viewModelScope.launch {
            repository.saveLocationProfile(
                SavedLocationProfileEntity(
                    id = 1,
                    isManualLocation = updatedState.isManualLocation,
                    locationSource = updatedState.locationSource,
                    latitude = updatedState.latitude,
                    longitude = updatedState.longitude,
                    address = updatedState.fullAddress,
                    plusCode = updatedState.plusCode,
                    street = updatedState.street,
                    subLocality = updatedState.subLocality,
                    locality = updatedState.locality,
                    city = updatedState.city,
                    province = updatedState.province,
                    postalCode = updatedState.postalCode,
                    country = updatedState.country,
                    showCountryFlag = updatedState.showCountryFlag,
                    altitudeMeters = updatedState.altitudeMeters,
                    projectNote = updatedState.projectNote
                )
            )
        }

        refreshMiniMap(force = true)
        reRenderActivePreviewIfNeeded()
    }

    fun triggerReverseGeocodeForCurrentCoordinates() {
        val loc = _locationState.value
        viewModelScope.launch {
            val geocoded = locationEngine.reverseGeocode(loc.latitude, loc.longitude)
            if (geocoded != null) {
                _locationState.update { current ->
                    current.copy(
                        street = geocoded.street.ifBlank { current.street },
                        subLocality = geocoded.subLocality.ifBlank { current.subLocality },
                        locality = geocoded.locality.ifBlank { current.locality },
                        city = geocoded.city.ifBlank { current.city },
                        province = geocoded.province.ifBlank { current.province },
                        postalCode = geocoded.postalCode.ifBlank { current.postalCode },
                        country = geocoded.country.ifBlank { current.country },
                        customFullAddressOverride = ""
                    )
                }
                _toastMessage.value = "Alamat diperbarui dari koordinat"
                reRenderActivePreviewIfNeeded()
            } else {
                _toastMessage.value = "Geocoder tidak tersedia — koordinat tetap disimpan"
            }
        }
    }

    // --- Stamp Style Controls ---
    fun setStampTemplate(template: StampTemplate) {
        _styleConfig.update { it.copy(template = template) }
        refreshMiniMap(force = true)
        reRenderActivePreviewIfNeeded()
    }

    fun setMiniMapStyle(miniMapStyle: MiniMapStyle) {
        _styleConfig.update { it.copy(miniMapStyle = miniMapStyle) }
        refreshMiniMap(force = true)
        reRenderActivePreviewIfNeeded()
    }

    fun setStampPosition(position: StampPosition) {
        _styleConfig.update { it.copy(position = position) }
        reRenderActivePreviewIfNeeded()
    }

    fun setStampOpacity(opacity: Float) {
        _styleConfig.update { it.copy(backgroundOpacity = opacity.coerceIn(0.40f, 0.95f)) }
        reRenderActivePreviewIfNeeded()
    }

    fun updateStampVisibility(
        showMiniMap: Boolean,
        showBrandBadgeTab: Boolean,
        showCompass: Boolean,
        showAltitudeAndAccuracy: Boolean,
        showProjectNote: Boolean
    ) {
        _styleConfig.update {
            it.copy(
                showMiniMap = showMiniMap,
                showBrandBadgeTab = showBrandBadgeTab,
                showCompass = showCompass,
                showAltitudeAndAccuracy = showAltitudeAndAccuracy,
                showProjectNote = showProjectNote
            )
        }
        reRenderActivePreviewIfNeeded()
    }

    // --- Capture, Gallery Import & Preview Baking ---
    fun onRawPhotoCaptured(rawBitmap: Bitmap) {
        if (!_timeConfig.value.isManualOverride) {
            _timeConfig.update { it.copy(currentAutoMillis = System.currentTimeMillis()) }
        }
        val cropped = cropToSelectedAspectRatio(rawBitmap, _aspectRatio.value)
        _rawCaptureBitmap.value = cropped
        bakePreviewBitmap(cropped)
    }

    fun captureSampleFieldScene() {
        val ratio = _aspectRatio.value
        val (w, h) = if (ratio == CameraAspectRatio.RATIO_4_3) {
            Pair(1200, 1600)
        } else {
            Pair(1080, 1920)
        }
        if (!_timeConfig.value.isManualOverride) {
            _timeConfig.update { it.copy(currentAutoMillis = System.currentTimeMillis()) }
        }
        viewModelScope.launch {
            _isRenderingPreview.value = true
            val sampleBmp = withContext(Dispatchers.Default) {
                BitmapStampRenderer.createSampleFieldPhotoBitmap(w, h)
            }
            _rawCaptureBitmap.value = sampleBmp
            bakePreviewBitmap(sampleBmp)
        }
    }

    fun onGalleryImagePicked(uri: Uri) {
        viewModelScope.launch {
            _isRenderingPreview.value = true
            val (decodedBmp, exifLatLon) = MediaStoreExifSaver.decodeBitmapFromUri(getApplication(), uri)
            if (decodedBmp == null) {
                _isRenderingPreview.value = false
                _toastMessage.value = "Gagal memuat gambar dari galeri"
                return@launch
            }
            if (exifLatLon != null && !_locationState.value.isManualLocation &&
                _locationState.value.locationSource == LocationSource.GPS
            ) {
                _locationState.update {
                    it.copy(
                        latitude = exifLatLon.first,
                        longitude = exifLatLon.second,
                        plusCode = PlusCodeGenerator.generateShortPlusCode(exifLatLon.first, exifLatLon.second)
                    )
                }
                refreshMiniMap(force = true)
            }
            _rawCaptureBitmap.value = decodedBmp
            bakePreviewBitmap(decodedBmp)
        }
    }

    private fun reRenderActivePreviewIfNeeded() {
        val raw = _rawCaptureBitmap.value ?: return
        bakePreviewBitmap(raw)
    }

    private fun bakePreviewBitmap(rawBitmap: Bitmap) {
        viewModelScope.launch {
            _isRenderingPreview.value = true
            val isSatellite = _styleConfig.value.effectiveSatelliteMap
            val currentMap = _miniMapBitmap.value ?: mapTileFetcher.getMiniMapBitmap(
                latitude = _locationState.value.latitude,
                longitude = _locationState.value.longitude,
                isSatellite = isSatellite,
                streetHint = _locationState.value.street
            ).also { _miniMapBitmap.value = it }

            val stamped = withContext(Dispatchers.Default) {
                BitmapStampRenderer.renderStampedBitmap(
                    sourceBitmap = rawBitmap,
                    timeConfig = _timeConfig.value,
                    location = _locationState.value,
                    styleConfig = _styleConfig.value,
                    miniMapBitmap = currentMap
                )
            }
            _previewStampedBitmap.value = stamped
            _isRenderingPreview.value = false
        }
    }

    fun discardPreview() {
        _rawCaptureBitmap.value = null
        _previewStampedBitmap.value = null
    }

    fun saveCurrentPreviewToGallery(onSavedSuccess: () -> Unit = {}) {
        val stamped = _previewStampedBitmap.value ?: return
        if (_isSavingPhoto.value) return

        viewModelScope.launch {
            _isSavingPhoto.value = true
            val tConfig = _timeConfig.value
            val loc = _locationState.value
            val sConfig = _styleConfig.value

            val result = MediaStoreExifSaver.saveStampedPhotoWithExif(
                context = getApplication(),
                stampedBitmap = stamped,
                timeConfig = tConfig,
                location = loc
            )

            result.onSuccess { saved ->
                val entity = StampedPhotoEntity(
                    mediaStoreUri = saved.mediaStoreUri,
                    localFilePath = saved.localFilePath,
                    displayTimestamp = if (sConfig.template == StampTemplate.MODERN) {
                        tConfig.formatTemplate2Timestamp()
                    } else {
                        tConfig.formatTemplate1Timestamp()
                    },
                    exifDateTimeOriginal = saved.exifDateTimeOriginal,
                    timeZoneLabel = tConfig.timeZoneOption.shortLabel,
                    isManualTime = tConfig.isManualOverride,
                    fullAddress = loc.fullAddress,
                    decimalCoordinates = loc.decimalCoordinatesText,
                    dmsCoordinates = loc.dmsCoordinatesText,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    locationSource = loc.locationSource,
                    plusCode = loc.plusCode,
                    magneticFieldUt = loc.magneticFieldUt,
                    altitudeMeters = loc.altitudeMeters,
                    azimuthDegrees = loc.azimuthDegrees,
                    cardinalDirection = loc.cardinalDirection,
                    templateName = sConfig.template.title,
                    projectNote = loc.projectNote
                )
                repository.insertPhoto(entity)
                _isSavingPhoto.value = false
                _rawCaptureBitmap.value = null
                _previewStampedBitmap.value = null
                _toastMessage.value = "Tersimpan di DCIM/GPSMapCamera (${loc.locationSource})"
                onSavedSuccess()
            }.onFailure { err ->
                _isSavingPhoto.value = false
                _toastMessage.value = "Gagal menyimpan foto: ${err.localizedMessage ?: "Error"}"
            }
        }
    }

    fun inspectPhotoExif(photo: StampedPhotoEntity) {
        viewModelScope.launch {
            _selectedPhotoExif.value = MediaStoreExifSaver.inspectExifFromFile(photo.localFilePath)
        }
    }

    fun clearSelectedPhotoExif() {
        _selectedPhotoExif.value = null
    }

    fun deleteHistoryPhoto(photo: StampedPhotoEntity) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            _toastMessage.value = "Foto dihapus dari riwayat aplikasi"
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun cropToSelectedAspectRatio(source: Bitmap, ratio: CameraAspectRatio): Bitmap {
        val isPortrait = source.height >= source.width
        val targetW = if (isPortrait) ratio.widthRatio else ratio.heightRatio
        val targetH = if (isPortrait) ratio.heightRatio else ratio.widthRatio
        val targetAspect = targetW.toFloat() / targetH.toFloat()
        val currentAspect = source.width.toFloat() / source.height.toFloat()

        if (abs(currentAspect - targetAspect) < 0.03f) return source

        return if (currentAspect > targetAspect) {
            val newWidth = (source.height * targetAspect).toInt().coerceIn(1, source.width)
            val xOffset = ((source.width - newWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, xOffset, 0, newWidth, source.height)
        } else {
            val newHeight = (source.width / targetAspect).toInt().coerceIn(1, source.height)
            val yOffset = ((source.height - newHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, 0, yOffset, source.width, newHeight)
        }
    }
}
