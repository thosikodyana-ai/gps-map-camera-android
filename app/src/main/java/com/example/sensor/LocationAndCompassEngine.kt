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
import org.json.JSONObject
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
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val azimuthRadians = orientationAngles[0]
                        val degrees = ((Math.toDegrees(azimuthRadians.toDouble()).toFloat() + 360f) % 360f)
                        if (abs(degrees - smoothedAzimuth) >= 1.0f) {
                            smoothedAzimuth = degrees.roundToInt().toFloat()
                        }
                        trySend(
                            CompassAndMagneticReading(
                                azimuthDegrees = smoothedAzimuth,
                                magneticFieldUt = smoothedMagUt
                            )
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sm.unregisterListener(listener)
        }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeocodedAddressParts? =
        withContext(Dispatchers.IO) {
            // 1. Try Android Platform Geocoder first
            val androidResult = reverseGeocodeViaAndroid(latitude, longitude)
            if (androidResult != null) return@withContext androidResult

            // 2. Fallback to OpenStreetMap Nominatim reverse geocoding API
            reverseGeocodeViaNominatim(latitude, longitude)
        }

    private suspend fun reverseGeocodeViaAndroid(latitude: Double, longitude: Double): GeocodedAddressParts? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("id", "ID"))
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

        return address?.let { addr ->
            val streetPart = buildString {
                if (!addr.thoroughfare.isNullOrBlank()) {
                    append(addr.thoroughfare)
                    if (!addr.subThoroughfare.isNullOrBlank()) {
                        append(" No. ").append(addr.subThoroughfare)
                    }
                } else if (!addr.featureName.isNullOrBlank()) {
                    append(addr.featureName)
                }
            }.ifBlank { "Titik Koordinat Peta" }

            val fullLine = (0..addr.maxAddressLineIndex)
                .mapNotNull { addr.getAddressLine(it) }
                .joinToString(", ")

            GeocodedAddressParts(
                street = streetPart,
                subLocality = addr.subLocality ?: "",
                locality = addr.locality ?: "",
                city = addr.subAdminArea ?: addr.locality ?: "",
                province = addr.adminArea ?: "",
                postalCode = addr.postalCode ?: "",
                country = addr.countryName ?: "Indonesia",
                formattedAddress = fullLine
            )
        }
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

                val road = addrObj.optString("road")
                    .ifBlank { addrObj.optString("pedestrian") }
                    .ifBlank { addrObj.optString("neighbourhood") }
                    .ifBlank { "Jalan Lokasi Terpilih" }
                val houseNumber = addrObj.optString("house_number")
                val streetFull = if (houseNumber.isNotBlank()) "$road No. $houseNumber" else road

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

                GeocodedAddressParts(
                    street = streetFull,
                    subLocality = village,
                    locality = district.ifBlank { village },
                    city = city,
                    province = state,
                    postalCode = postcode,
                    country = country,
                    formattedAddress = displayName
                )
            }
        }.getOrNull()
    }
}
