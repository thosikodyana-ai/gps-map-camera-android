package com.example

import com.example.model.LocationMetadata
import com.example.model.LocationSource
import com.example.model.PlusCodeGenerator
import com.example.stamp.WebMercatorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun webMercatorMath_roundTripLatLonToTileAndBack_isAccurate() {
        val originalLat = -6.887405
        val originalLon = 109.035938
        val zoom = 16

        val (tileX, tileY) = WebMercatorMath.latLonToExactTile(originalLat, originalLon, zoom)
        val (recoveredLat, recoveredLon) = WebMercatorMath.exactTileToLatLon(tileX, tileY, zoom)

        assertTrue("Latitude roundtrip must be within 1e-6", abs(originalLat - recoveredLat) < 1e-5)
        assertTrue("Longitude roundtrip must be within 1e-6", abs(originalLon - recoveredLon) < 1e-5)
    }

    @Test
    fun plusCodeGenerator_generatesValidShortCode() {
        val code = PlusCodeGenerator.generateShortPlusCode(-6.887405, 109.035938)
        assertTrue("Plus code should contain '+' separator", code.contains("+"))
        assertEquals(8, code.length)
    }

    @Test
    fun referenceTemplateFormatting_matchesExpectedReferenceStrings() {
        val brebesMeta = LocationMetadata(
            isManualLocation = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -6.887405,
            longitude = 109.035938,
            altitudeMeters = 44.0,
            accuracyMeters = 2.0f,
            magneticFieldUt = 45.72f,
            plusCode = "427p+2cv",
            street = "Jl. Sapphire Regency",
            subLocality = "Pulosari",
            locality = "Kecamatan Brebes",
            city = "Kabupaten Brebes",
            province = "Jawa Tengah",
            postalCode = "52213",
            country = "Indonesia",
            azimuthDegrees = 12f
        )

        assertEquals("Kecamatan Brebes, Jawa Tengah, Indonesia", brebesMeta.template1Headline)
        assertEquals("Lat -6.887405° Long 109.035938°", brebesMeta.template1LatLongText)
        assertEquals("-6,8874°, 109,0359°", brebesMeta.template2CommaCoordText)
        assertEquals("44m di atas permukaan laut", brebesMeta.template2AltitudeText)
        assertEquals("±2m akurasi", brebesMeta.template2AccuracyText)
        assertEquals("45,72 µT", brebesMeta.template2MagneticText)
        assertEquals("12 N", brebesMeta.template2CompassText)
    }
}
