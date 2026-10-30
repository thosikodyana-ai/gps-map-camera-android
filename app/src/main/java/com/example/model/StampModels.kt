package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor

enum class TimeZoneOption(
    val shortLabel: String,
    val displayName: String,
    val zoneId: String,
    val exifOffset: String,
    val gmtLabelColonial: String,
    val gmtLabelCompact: String
) {
    WIB("WIB", "WIB (GMT+07:00 • Jakarta)", "Asia/Jakarta", "+07:00", "GMT +07:00", "GMT +7:00"),
    WITA("WITA", "WITA (GMT+08:00 • Makassar)", "Asia/Makassar", "+08:00", "GMT +08:00", "GMT +8:00"),
    WIT("WIT", "WIT (GMT+09:00 • Jayapura)", "Asia/Jayapura", "+09:00", "GMT +09:00", "GMT +9:00"),
    UTC("UTC", "UTC (GMT+00:00 • Universal)", "UTC", "+00:00", "GMT +00:00", "GMT +0:00")
}

enum class DateFormatOption(
    val pattern: String,
    val exampleLabel: String
) {
    REF_TEMPLATE_1("EEEE, dd/MM/yyyy hh:mm a", "Jumat, 30/10/2026 01:47 PM"),
    REF_TEMPLATE_2("EEE, MMM dd, yyyy, HH:mm", "Jum, Okt 30, 2026, 13:53"),
    SLASH_24H("dd/MM/yyyy HH:mm:ss", "30/10/2026 14:35:08"),
    ISO_12H("yyyy-MM-dd hh:mm:ss a", "2026-10-30 02:35:08 PM")
}

data class TimeConfig(
    val isManualOverride: Boolean = false,
    val customEpochMillis: Long = System.currentTimeMillis(),
    val currentAutoMillis: Long = System.currentTimeMillis(),
    val timeZoneOption: TimeZoneOption = TimeZoneOption.WIB,
    val formatOption: DateFormatOption = DateFormatOption.REF_TEMPLATE_1
) {
    val effectiveEpochMillis: Long
        get() = if (isManualOverride) customEpochMillis else currentAutoMillis

    fun formatDisplayTimestamp(): String {
        val locale = Locale.forLanguageTag("id-ID")
        val sdf = SimpleDateFormat(formatOption.pattern, locale).apply {
            timeZone = TimeZone.getTimeZone(timeZoneOption.zoneId)
        }
        return "${sdf.format(Date(effectiveEpochMillis))} ${timeZoneOption.gmtLabelColonial}"
    }

    /**
     * Matches Reference Image 1: "Jumat, 30/10/2026 01:47 PM GMT +07:00"
     * while respecting custom format if the user explicitly switched to 24H/ISO.
     */
    fun formatTemplate1Timestamp(): String {
        val locale = Locale.forLanguageTag("id-ID")
        val pattern = when (formatOption) {
            DateFormatOption.REF_TEMPLATE_1 -> "EEEE, dd/MM/yyyy hh:mm a"
            DateFormatOption.REF_TEMPLATE_2 -> "EEEE, dd/MM/yyyy HH:mm"
            else -> formatOption.pattern
        }
        val sdf = SimpleDateFormat(pattern, locale).apply {
            timeZone = TimeZone.getTimeZone(timeZoneOption.zoneId)
        }
        return "${sdf.format(Date(effectiveEpochMillis))} ${timeZoneOption.gmtLabelColonial}"
    }

    /**
     * Matches Reference Image 2: "Jum, Okt 30, 2026, 13:53, GMT +7:00"
     */
    fun formatTemplate2Timestamp(): String {
        val locale = Locale.forLanguageTag("id-ID")
        val pattern = when (formatOption) {
            DateFormatOption.REF_TEMPLATE_1,
            DateFormatOption.REF_TEMPLATE_2 -> "EEE, MMM dd, yyyy, HH:mm"
            else -> formatOption.pattern
        }
        val sdf = SimpleDateFormat(pattern, locale).apply {
            timeZone = TimeZone.getTimeZone(timeZoneOption.zoneId)
        }
        return "${sdf.format(Date(effectiveEpochMillis))}, ${timeZoneOption.gmtLabelCompact}"
    }

    fun formatExifDateTime(): String {
        val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(timeZoneOption.zoneId)
        }
        return sdf.format(Date(effectiveEpochMillis))
    }
}

object LocationSource {
    const val GPS = "gps"
    const val MANUAL_MAP = "manual_map"
    const val MANUAL_FORM = "manual_form"
}

/**
 * Generates short Open Location Code (Plus Code) like "427p+2cv" from latitude & longitude.
 */
object PlusCodeGenerator {
    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private val PLUS_CODE_REGEX = Regex("^[23456789CFGHJMPQRVWXcfghjmpqrvwx]{4,8}\\+[23456789CFGHJMPQRVWXcfghjmpqrvwx]{2,4}$")

    fun isPlusCodeToken(token: String): Boolean {
        return PLUS_CODE_REGEX.matches(token.trim())
    }

    fun stripLeadingPlusCodes(address: String): String {
        val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val filtered = parts.dropWhile { isPlusCodeToken(it) }
        return if (filtered.isNotEmpty()) filtered.joinToString(", ") else address.trim()
    }

    fun generateShortPlusCode(latitude: Double, longitude: Double): String {
        val clampedLat = (latitude.coerceIn(-89.999999, 89.999999) + 90.0)
        val clampedLon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0

        var latVal = floor(clampedLat * 8000.0).toLong()
        var lonVal = floor(clampedLon * 8000.0).toLong()

        val chars = CharArray(10)
        for (i in 4 downTo 0) {
            val lonDigit = (lonVal % 20).toInt().coerceIn(0, 19)
            val latDigit = (latVal % 20).toInt().coerceIn(0, 19)
            chars[i * 2 + 1] = CODE_ALPHABET[lonDigit]
            chars[i * 2] = CODE_ALPHABET[latDigit]
            lonVal /= 20
            latVal /= 20
        }
        val prefix4 = String(chars, 4, 4).lowercase(Locale.US)
        val suffix2 = String(chars, 8, 2).lowercase(Locale.US)
        val extraChar = CODE_ALPHABET[((abs(latitude * 100000).toInt() + abs(longitude * 100000).toInt()) % 20)]
            .lowercaseChar()
        return "$prefix4+$suffix2$extraChar"
    }
}

data class LocationMetadata(
    val isManualLocation: Boolean = false,
    val locationSource: String = LocationSource.GPS,
    val isGpsLocked: Boolean = false,
    val latitude: Double = -6.887405,
    val longitude: Double = 109.035938,
    val altitudeMeters: Double = 44.0,
    val accuracyMeters: Float = 2.0f,
    val magneticFieldUt: Float = 45.72f,
    val plusCode: String = "427p+2cv",
    val street: String = "Jl. Sapphire Regency",
    val subLocality: String = "Pulosari",
    val locality: String = "Kecamatan Brebes",
    val city: String = "Kabupaten Brebes",
    val province: String = "Jawa Tengah",
    val postalCode: String = "52213",
    val country: String = "Indonesia",
    val showCountryFlag: Boolean = true,
    val azimuthDegrees: Float = 12f,
    val projectNote: String = "Captured by GPS Map Camera",
    val customFullAddressOverride: String = ""
) {
    /**
     * Header for Reference Template 1 (Image 1 / Green Circle):
     * "Kecamatan Brebes, Jawa Tengah, Indonesia"
     */
    val template1Headline: String
        get() = listOf(
            locality.ifBlank { city.ifBlank { subLocality } },
            province,
            country
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

    /**
     * Body address for Reference Template 1 (Image 1 / Green Circle):
     * "427p+2cv, Jl. Sapphire Regency, Pulosari, Kec. Brebes, Kabupaten Brebes, Jawa Tengah 52213, Indonesia"
     * Guarantees no duplicate Plus Code at the start of the address.
     */
    val template1FullAddressWithPlusCode: String
        get() {
            val codePrefix = plusCode.trim()
            val cleanedBase = PlusCodeGenerator.stripLeadingPlusCodes(fullAddress)
            return if (codePrefix.isNotEmpty() && !cleanedBase.startsWith(codePrefix, ignoreCase = true)) {
                "$codePrefix, $cleanedBase"
            } else {
                cleanedBase
            }
        }

    /**
     * Header for Reference Template 2 (Image 2):
     * "1-Y Zachat'yevskiy Pereulok, 15, Moskva, Rusia, 119034"
     */
    val template2AddressHeader: String
        get() = if (customFullAddressOverride.isNotBlank()) {
             customFullAddressOverride
        } else {
            val cleanedStreet = street.trim().takeUnless { PlusCodeGenerator.isPlusCodeToken(it) } ?: ""
            val codeFallback = plusCode.trim().uppercase(Locale.US)
            listOf(
                cleanedStreet.ifBlank { codeFallback },
                subLocality,
                city.ifBlank { locality },
                country,
                postalCode
            ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
        }

    val fullAddress: String
        get() = if (customFullAddressOverride.isNotBlank()) {
            PlusCodeGenerator.stripLeadingPlusCodes(customFullAddressOverride)
        } else {
            val cleanedStreet = street.trim().takeUnless { PlusCodeGenerator.isPlusCodeToken(it) } ?: ""
            val provWithPostal = listOf(province.trim(), postalCode.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            listOf(
                cleanedStreet,
                subLocality,
                locality,
                city,
                provWithPostal,
                country
            ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
        }

    /**
     * Reference Image 1 format: "Lat -6.887405° Long 109.035938°"
     */
    val template1LatLongText: String
        get() = String.format(Locale.US, "Lat %.6f° Long %.6f°", latitude, longitude)

    /**
     * Reference Image 2 format with comma decimal separator: "55,7399°, 37,6016°"
     */
    val template2CommaCoordText: String
        get() {
            val latStr = String.format(Locale.US, "%.4f", latitude).replace('.', ',')
            val lonStr = String.format(Locale.US, "%.4f", longitude).replace('.', ',')
            return "$latStr°, $lonStr°"
        }

    val template2AltitudeText: String
        get() = "${altitudeMeters.toInt()}m di atas permukaan laut"

    val template2AccuracyText: String
        get() = "±${accuracyMeters.toInt().coerceAtLeast(1)}m akurasi"

    val template2MagneticText: String
        get() = String.format(Locale.US, "%.2f µT", magneticFieldUt).replace('.', ',')

    val template2CompassText: String
        get() = "${azimuthDegrees.toInt()} ${cardinalEnglishShort}"

    val decimalCoordinatesText: String
        get() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    val dmsCoordinatesText: String
        get() = "${toDms(latitude, isLatitude = true)}  ${toDms(longitude, isLatitude = false)}"

    val cardinalDirection: String
        get() = azimuthToCardinal(azimuthDegrees)

    val cardinalEnglishShort: String
        get() = azimuthToEnglishCardinal(azimuthDegrees)

    companion object {
        fun toDms(coord: Double, isLatitude: Boolean): String {
            val hemisphere = if (isLatitude) {
                if (coord >= 0) "N" else "S"
            } else {
                if (coord >= 0) "E" else "W"
            }
            val absolute = abs(coord)
            val degrees = absolute.toInt()
            val minutesFull = (absolute - degrees) * 60.0
            val minutes = minutesFull.toInt()
            val seconds = (minutesFull - minutes) * 60.0
            return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, hemisphere)
        }

        fun azimuthToCardinal(degrees: Float): String {
            val normalized = ((degrees % 360f) + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> "U (N)"
                normalized < 67.5f -> "TL (NE)"
                normalized < 112.5f -> "T (E)"
                normalized < 157.5f -> "TG (SE)"
                normalized < 202.5f -> "S (S)"
                normalized < 247.5f -> "BD (SW)"
                normalized < 292.5f -> "B (W)"
                else -> "BL (NW)"
            }
        }

        fun azimuthToEnglishCardinal(degrees: Float): String {
            val normalized = ((degrees % 360f) + 360f) % 360f
            return when {
                normalized >= 337.5f || normalized < 22.5f -> "N"
                normalized < 67.5f -> "NE"
                normalized < 112.5f -> "E"
                normalized < 157.5f -> "SE"
                normalized < 202.5f -> "S"
                normalized < 247.5f -> "SW"
                normalized < 292.5f -> "W"
                else -> "NW"
            }
        }
    }
}

enum class StampTemplate(val title: String, val subtitle: String) {
    CLASSIC(
        "Template 1 (Gaya Satelit & Badge)",
        "Persis Gambar 1: Peta Satelit kiri, tab 'GPS Map Camera', bendera 🇮🇩, Plus Code, Lat/Long & Note"
    ),
    MODERN(
        "Template 2 (Gaya Peta Jalan & Sensor µT)",
        "Persis Gambar 2: Peta Jalan terang kiri, ikon koordinat ⌖, jam 🕒, mdpl, akurasi ±m, µT & kompas"
    ),
    MINIMALIST(
        "Minimalist Ringkas",
        "Tampilan ringkas dengan peta mini, waktu, alamat, dan koordinat"
    )
}

enum class MiniMapStyle(val label: String) {
    AUTO_BY_TEMPLATE("Otomatis Sesuai Template"),
    SATELLITE("Satelit (Gambar 1)"),
    STREET_LIGHT("Peta Jalan Terang (Gambar 2)")
}

enum class StampPosition(val label: String) {
    BOTTOM("Bawah"),
    TOP("Atas")
}

enum class CameraAspectRatio(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_4_3("4:3", 3, 4),
    RATIO_16_9("16:9", 9, 16)
}

enum class FlashModeOption(val label: String) {
    OFF("Mati"),
    AUTO("Otomatis"),
    ON("Nyala")
}

data class StampStyleConfig(
    val template: StampTemplate = StampTemplate.CLASSIC,
    val miniMapStyle: MiniMapStyle = MiniMapStyle.AUTO_BY_TEMPLATE,
    val position: StampPosition = StampPosition.BOTTOM,
    val backgroundOpacity: Float = 0.74f,
    val showMiniMap: Boolean = true,
    val showBrandBadgeTab: Boolean = true,
    val showCompass: Boolean = true,
    val showAltitudeAndAccuracy: Boolean = true,
    val showProjectNote: Boolean = true
) {
    val effectiveSatelliteMap: Boolean
        get() = when (miniMapStyle) {
            MiniMapStyle.SATELLITE -> true
            MiniMapStyle.STREET_LIGHT -> false
            MiniMapStyle.AUTO_BY_TEMPLATE -> template == StampTemplate.CLASSIC
        }
}
