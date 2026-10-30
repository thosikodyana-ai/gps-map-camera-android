package com.example.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import com.example.model.PlusCodeGenerator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class GeocodedAddressParts(
    val street: String,
    val subLocality: String,
    val locality: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String = "Indonesia",
    val formattedAddress: String = ""
)

data class PlaceSearchResult(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val addressParts: GeocodedAddressParts? = null
)

data class CompassAndMagneticReading(
    val azimuthDegrees: Float,
    val magneticFieldUt: Float
)

class LocationAndCompassEngine(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun observeLiveLocations(scope: CoroutineScope): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(loc)
            }
        }

        runCatching {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    trySend(lastLoc)
                }
            }
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        }

        awaitClose {
            runCatching {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getOneShotCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        runCatching {
            suspendCoroutine<Location?> { cont ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { fallbackLoc -> cont.resume(fallbackLoc) }
                                .addOnFailureListener { cont.resume(null) }
                        }
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            }
        }.getOrNull()
    }

    /**
     * Observes Compass Azimuth & Magnetic Field (µT) with 400ms throttling + delta threshold
     * so the Compose UI stays 60 FPS smooth without redundant recompositions.
     */
    fun observeCompassAndMagnetic(): Flow<CompassAndMagneticReading> = callbackFlow {
        val sm = sensorManager
        val accelerometer = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sm == null || accelerometer == null || magnetometer == null) {
            trySend(CompassAndMagneticReading(azimuthDegrees = 12f, magneticFieldUt = 45.72f))
            awaitClose { }
            return@callbackFlow
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var hasGravity = false
        var hasMagnetic = false
        var smoothedAzimuth = 12f
        var smoothedMagUt = 45.72f
        var lastEmittedAzimuth = -999f
        var lastEmittedMag = -999f
        var lastEmitElapsedMs = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val alpha = 0.88f
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val bx = event.values[0]
                        val by = event.values[1]
                        val bz = event.values[2]
                        geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * bx
                        geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * by
                        geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * bz
                        val magnitude = sqrt(bx * bx + by * by + bz * bz)
                        smoothedMagUt = (alpha * smoothedMagUt + (1 - alpha) * magnitude)
                            .coerceIn(10f, 250f)
                        hasMagnetic = true
                    }
                }
                if (hasGravity && hasMagnetic) {
                    val nowMs = SystemClock.elapsedRealtime()
                    // Throttle sensor UI updates to max 2.5 times/second (every 400ms) for smooth 60 FPS UI
                    if (nowMs - lastEmitElapsedMs < 400L) return

                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val azimuthRadians = orientationAngles[0]
                        val degrees = ((Math.toDegrees(azimuthRadians.toDouble()).toFloat() + 360f) % 360f)
                        smoothedAzimuth = degrees.roundToInt().toFloat()

                        val azimuthChanged = abs(smoothedAzimuth - lastEmittedAzimuth) >= 2.0f
                        val magChanged = abs(smoothedMagUt - lastEmittedMag) >= 0.25f
                        if (azimuthChanged || magChanged) {
                            lastEmittedAzimuth = smoothedAzimuth
                            lastEmittedMag = smoothedMagUt
                            lastEmitElapsedMs = nowMs
                            trySend(
                                CompassAndMagneticReading(
                                    azimuthDegrees = smoothedAzimuth,
                                    magneticFieldUt = smoothedMagUt
                                )
                            )
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sm.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sm.unregisterListener(listener)
        }
    }

    /**
     * Searches places, streets, villages, cities, or raw coordinates for the Interactive Map Picker.
     */
    suspend fun searchPlaces(query: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<PlaceSearchResult>()

        // 1. Check if query is direct coordinates ("-6.8874, 109.0359")
        parseCoordinateQuery(trimmed)?.let { (lat, lon) ->
            val reverse = reverseGeocode(lat, lon)
            val subtitle = reverse?.formattedAddress?.ifBlank { null }
                ?: String.format(Locale.US, "Titik Koordinat (%.6f, %.6f)", lat, lon)
            results.add(
                PlaceSearchResult(
                    title = String.format(Locale.US, "Koordinat %.6f, %.6f", lat, lon),
                    subtitle = subtitle,
                    latitude = lat,
                    longitude = lon,
                    addressParts = reverse
                )
            )
        }

        // 2. Query OpenStreetMap Nominatim Search API (supports detailed Indonesian streets/villages/cities)
        val nominatimMatches = searchViaNominatim(trimmed)
        results.addAll(nominatimMatches)

        // 3. Fallback / supplement with Android Built-in Geocoder
        if (results.size < 3) {
            val androidMatches = searchViaAndroidGeocoder(trimmed)
            for (item in androidMatches) {
                val duplicate = results.any {
                    abs(it.latitude - item.latitude) < 0.001 && abs(it.longitude - item.longitude) < 0.001
                }
                if (!duplicate) {
                    results.add(item)
                }
            }
        }

        // 4. Local offline index fallback so search works even when completely offline
        if (results.isEmpty()) {
            val lower = trimmed.lowercase(Locale.US)
            offlineIndonesianPlaces.filter {
                it.title.lowercase(Locale.US).contains(lower) ||
                    it.subtitle.lowercase(Locale.US).contains(lower)
            }.forEach { results.add(it) }
        }

        results.take(6)
    }

    private fun parseCoordinateQuery(query: String): Pair<Double, Double>? {
        val parts = query.split(",", ";", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Pair(lat, lon)
    }

    private fun searchViaNominatim(query: String): List<PlaceSearchResult> {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=6&addressdetails=1&accept-language=id"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPSMapCameraAndroid/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val bodyStr = response.body?.string() ?: return@runCatching emptyList()
                val jsonArray = JSONArray(bodyStr)
                val list = mutableListOf<PlaceSearchResult>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                    val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                    val displayName = obj.optString("display_name", "")
                    val name = obj.optString("name", "").ifBlank {
                        displayName.substringBefore(",").trim()
                    }
                    val addrObj = obj.optJSONObject("address")
                    val parts = addrObj?.let { parseNominatimAddressObject(it, displayName) }

                    list.add(
                        PlaceSearchResult(
                            title = name.ifBlank { "Lokasi Terpilih" },
                            subtitle = displayName,
                            latitude = lat,
                            longitude = lon,
                            addressParts = parts
                        )
                    )
                }
                list
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun searchViaAndroidGeocoder(query: String): List<PlaceSearchResult> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.forLanguageTag("id-ID"))
        val addresses: List<Address> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addrs: MutableList<Address>) {
                            cont.resume(addrs)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(emptyList())
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 5).orEmpty()
            }
        }.getOrDefault(emptyList())

        return addresses.mapNotNull { addr ->
            if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
            val parts = addressToParts(addr)
            val title = addr.featureName?.takeUnless { PlusCodeGenerator.isPlusCodeToken(it) }
                ?: parts.street.ifBlank { parts.locality.ifBlank { parts.city } }
            PlaceSearchResult(
                title = title.ifBlank { query },
                subtitle = parts.formattedAddress.ifBlank {
                    listOf(parts.street, parts.subLocality, parts.locality, parts.city, parts.province)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                },
                latitude = addr.latitude,
                longitude = addr.longitude,
                addressParts = parts
            )
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddressParts? =
        withContext(Dispatchers.IO) {
            val androidResult = reverseGeocodeViaAndroid(latitude, longitude)
            if (androidResult != null) return@withContext androidResult

            reverseGeocodeViaNominatim(latitude, longitude)
        }

    private suspend fun reverseGeocodeViaAndroid(latitude: Double, longitude: Double): GeocodedAddressParts? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("id-ID"))
        val address: Address? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCoroutine<Address?> { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }.getOrNull()

        return address?.let { addressToParts(it) }
    }

    private fun addressToParts(addr: Address): GeocodedAddressParts {
        val rawFeature = addr.featureName?.trim().orEmpty()
        val nonPlusCodeFeature = rawFeature.takeUnless { PlusCodeGenerator.isPlusCodeToken(it) }.orEmpty()

        val streetPart = buildString {
            if (!addr.thoroughfare.isNullOrBlank()) {
                append(addr.thoroughfare)
                if (!addr.subThoroughfare.isNullOrBlank()) {
                    append(" No. ").append(addr.subThoroughfare)
                }
            } else if (nonPlusCodeFeature.isNotBlank()) {
                append(nonPlusCodeFeature)
            }
        }

        val fullLineRaw = (0..addr.maxAddressLineIndex)
            .mapNotNull { addr.getAddressLine(it) }
            .joinToString(", ")
        val fullLineCleaned = PlusCodeGenerator.stripLeadingPlusCodes(fullLineRaw)

        return GeocodedAddressParts(
            street = streetPart.ifBlank { addr.subLocality ?: "" },
            subLocality = addr.subLocality ?: "",
            locality = addr.locality ?: "",
            city = addr.subAdminArea ?: addr.locality ?: "",
            province = addr.adminArea ?: "",
            postalCode = addr.postalCode ?: "",
            country = addr.countryName ?: "Indonesia",
            formattedAddress = fullLineCleaned
        )
    }

    private fun reverseGeocodeViaNominatim(latitude: Double, longitude: Double): GeocodedAddressParts? {
        return runCatching {
            val url = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&accept-language=id",
                latitude,
                longitude
            )
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPSMapCameraAndroid/1.0")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bodyStr = response.body?.string() ?: return@runCatching null
                val json = JSONObject(bodyStr)
                val displayName = json.optString("display_name", "")
                val addrObj = json.optJSONObject("address") ?: return@runCatching null
                parseNominatimAddressObject(addrObj, displayName)
            }
        }.getOrNull()
    }

    private fun parseNominatimAddressObject(addrObj: JSONObject, displayName: String): GeocodedAddressParts {
        val road = addrObj.optString("road")
            .ifBlank { addrObj.optString("pedestrian") }
            .ifBlank { addrObj.optString("neighbourhood") }
            .ifBlank { addrObj.optString("suburb") }
        val houseNumber = addrObj.optString("house_number")
        val streetFull = if (houseNumber.isNotBlank() && road.isNotBlank()) "$road No. $houseNumber" else road

        val village = addrObj.optString("village")
            .ifBlank { addrObj.optString("suburb") }
            .ifBlank { addrObj.optString("hamlet") }
        val district = addrObj.optString("city_district")
            .ifBlank { addrObj.optString("municipality") }
            .ifBlank { addrObj.optString("county") }
        val city = addrObj.optString("city")
            .ifBlank { addrObj.optString("town") }
            .ifBlank { addrObj.optString("regency") }
            .ifBlank { district }
        val state = addrObj.optString("state")
        val postcode = addrObj.optString("postcode")
        val country = addrObj.optString("country", "Indonesia")

        return GeocodedAddressParts(
            street = streetFull,
            subLocality = village,
            locality = district.ifBlank { village },
            city = city,
            province = state,
            postalCode = postcode,
            country = country,
            formattedAddress = PlusCodeGenerator.stripLeadingPlusCodes(displayName)
        )
    }

    companion object {
        private val offlineIndonesianPlaces = listOf(
            PlaceSearchResult(
                title = "Procot, Kecamatan Slawi, Tegal",
                subtitle = "Procot, Kecamatan Slawi, Kabupaten Tegal, Jawa Tengah 52411, Indonesia",
                latitude = -6.978291,
                longitude = 109.140948,
                addressParts = GeocodedAddressParts(
                    street = "Procot",
                    subLocality = "Procot",
                    locality = "Kecamatan Slawi",
                    city = "Kabupaten Tegal",
                    province = "Jawa Tengah",
                    postalCode = "52411",
                    country = "Indonesia",
                    formattedAddress = "Procot, Kecamatan Slawi, Kabupaten Tegal, Jawa Tengah 52411, Indonesia"
                )
            ),
            PlaceSearchResult(
                title = "Jl. Sapphire Regency, Brebes",
                subtitle = "Jl. Sapphire Regency, Pulosari, Kec. Brebes, Kabupaten Brebes, Jawa Tengah 52213, Indonesia",
                latitude = -6.887405,
                longitude = 109.035938,
                addressParts = GeocodedAddressParts(
                    street = "Jl. Sapphire Regency",
                    subLocality = "Pulosari",
                    locality = "Kecamatan Brebes",
                    city = "Kabupaten Brebes",
                    province = "Jawa Tengah",
                    postalCode = "52213",
                    country = "Indonesia",
                    formattedAddress = "Jl. Sapphire Regency, Pulosari, Kec. Brebes, Kabupaten Brebes, Jawa Tengah 52213, Indonesia"
                )
            ),
            PlaceSearchResult(
                title = "Monumen Nasional (Monas), Jakarta Pusat",
                subtitle = "Jl. Medan Merdeka Utara, Gambir, Kecamatan Gambir, Jakarta Pusat, DKI Jakarta 10110, Indonesia",
                latitude = -6.175392,
                longitude = 106.827153,
                addressParts = GeocodedAddressParts(
                    street = "Jl. Medan Merdeka Utara",
                    subLocality = "Gambir",
                    locality = "Kecamatan Gambir",
                    city = "Jakarta Pusat",
                    province = "DKI Jakarta",
                    postalCode = "10110",
                    country = "Indonesia",
                    formattedAddress = "Jl. Medan Merdeka Utara, Gambir, Jakarta Pusat, DKI Jakarta 10110, Indonesia"
                )
            ),
            PlaceSearchResult(
                title = "Simpang Lima, Kota Semarang",
                subtitle = "Jl. Pandanaran, Mugassari, Kec. Semarang Selatan, Kota Semarang, Jawa Tengah 50249, Indonesia",
                latitude = -6.990399,
                longitude = 110.422910,
                addressParts = GeocodedAddressParts(
                    street = "Jl. Pandanaran",
                    subLocality = "Mugassari",
                    locality = "Kecamatan Semarang Selatan",
                    city = "Kota Semarang",
                    province = "Jawa Tengah",
                    postalCode = "50249",
                    country = "Indonesia",
                    formattedAddress = "Jl. Pandanaran, Mugassari, Kec. Semarang Selatan, Kota Semarang, Jawa Tengah 50249, Indonesia"
                )
            ),
            PlaceSearchResult(
                title = "Gedung Sate, Kota Bandung",
                subtitle = "Jl. Diponegoro No. 22, Citarum, Kec. Bandung Wetan, Kota Bandung, Jawa Barat 40115, Indonesia",
                latitude = -6.902481,
                longitude = 107.618810,
                addressParts = GeocodedAddressParts(
                    street = "Jl. Diponegoro No. 22",
                    subLocality = "Citarum",
                    locality = "Kecamatan Bandung Wetan",
                    city = "Kota Bandung",
                    province = "Jawa Barat",
                    postalCode = "40115",
                    country = "Indonesia",
                    formattedAddress = "Jl. Diponegoro No. 22, Citarum, Kec. Bandung Wetan, Kota Bandung, Jawa Barat 40115, Indonesia"
                )
            ),
            PlaceSearchResult(
                title = "Tunjungan, Kota Surabaya",
                subtitle = "Jl. Tunjungan No. 65, Genteng, Kec. Genteng, Kota Surabaya, Jawa Timur 60275, Indonesia",
                latitude = -7.257472,
                longitude = 112.752088,
                addressParts = GeocodedAddressParts(
                    street = "Jl. Tunjungan No. 65",
                    subLocality = "Genteng",
                    locality = "Kecamatan Genteng",
                    city = "Kota Surabaya",
                    province = "Jawa Timur",
                    postalCode = "60275",
                    country = "Indonesia",
                    formattedAddress = "Jl. Tunjungan No. 65, Genteng, Kec. Genteng, Kota Surabaya, Jawa Timur 60275, Indonesia"
                )
            )
        )
    }
}
