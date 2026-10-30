package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PhotoHistoryRepository
import com.example.data.local.SavedLocationProfileEntity
import com.example.model.LocationMetadata
import com.example.model.LocationSource
import com.example.model.StampStyleConfig
import com.example.model.StampTemplate
import com.example.model.TimeConfig
import com.example.stamp.BitmapStampRenderer
import com.example.viewmodel.CameraStampViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_matchesGpsMapCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GPS Map Camera", appName)
    }

    @Test
    fun manualMapLocation_persistsToDatabaseAndReloadsCorrectly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = PhotoHistoryRepository(db.stampedPhotoDao())

        val manualProfile = SavedLocationProfileEntity(
            id = 1,
            isManualLocation = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -7.257472,
            longitude = 112.752088,
            address = "Jl. Tunjungan No. 65, Genteng, Kota Surabaya, Jawa Timur 60275, Indonesia",
            plusCode = "pqr8+2v",
            street = "Jl. Tunjungan No. 65",
            subLocality = "Genteng",
            locality = "Kec. Genteng",
            city = "Kota Surabaya",
            province = "Jawa Timur",
            postalCode = "60275",
            country = "Indonesia",
            showCountryFlag = true,
            altitudeMeters = 8.2,
            projectNote = "Titik Peta Surabaya"
        )

        repository.saveLocationProfile(manualProfile)
        val loaded = repository.getSavedLocationProfileOnce()

        assertNotNull(loaded)
        assertEquals(LocationSource.MANUAL_MAP, loaded?.locationSource)
        assertEquals(-7.257472, loaded!!.latitude, 1e-6)
        assertEquals(112.752088, loaded.longitude, 1e-6)
        assertTrue(loaded.address.contains("Jl. Tunjungan No. 65"))
        db.close()
    }

    @Test
    fun manualMapLocation_isNeverOverwrittenByDeviceGpsUpdates() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CameraStampViewModel(application)

        // 1. User confirms manual location from Interactive Map Picker ("manual_map")
        val chosenLat = -6.902481
        val chosenLon = 107.618810
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = chosenLat,
            longitude = chosenLon,
            altitudeMeters = 768.0,
            plusCode = "3jxp+gc",
            street = "Jl. Diponegoro No. 22",
            subLocality = "Citarum",
            locality = "Kec. Bandung Wetan",
            city = "Kota Bandung",
            province = "Jawa Barat",
            postalCode = "40115",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Gedung Sate Bandung"
        )

        assertEquals(LocationSource.MANUAL_MAP, viewModel.locationState.value.locationSource)
        assertEquals(chosenLat, viewModel.locationState.value.latitude, 1e-6)
        assertEquals(chosenLon, viewModel.locationState.value.longitude, 1e-6)

        // 2. Simulate incoming device GPS update from completely different coordinates
        viewModel.onIncomingDeviceGpsLocation(
            latitude = -6.175392,
            longitude = 106.827153,
            altitudeMeters = 12.0,
            accuracyMeters = 3.0f
        )

        // 3. Verify manual map location was NOT overwritten by device GPS
        assertEquals(LocationSource.MANUAL_MAP, viewModel.locationState.value.locationSource)
        assertEquals(chosenLat, viewModel.locationState.value.latitude, 1e-6)
        assertEquals(chosenLon, viewModel.locationState.value.longitude, 1e-6)
        assertEquals("Kota Bandung", viewModel.locationState.value.city)
    }

    @Test
    fun bitmapStampRenderer_rendersBothReferenceTemplatesSuccessfully() {
        val rawPhoto = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val miniMap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val timeConfig = TimeConfig()
        val location = LocationMetadata()

        val renderedTemplate1 = BitmapStampRenderer.renderStampedBitmap(
            sourceBitmap = rawPhoto,
            timeConfig = timeConfig,
            location = location,
            styleConfig = StampStyleConfig(template = StampTemplate.CLASSIC),
            miniMapBitmap = miniMap
        )
        assertEquals(800, renderedTemplate1.width)
        assertEquals(600, renderedTemplate1.height)

        val renderedTemplate2 = BitmapStampRenderer.renderStampedBitmap(
            sourceBitmap = rawPhoto,
            timeConfig = timeConfig,
            location = location,
            styleConfig = StampStyleConfig(template = StampTemplate.MODERN),
            miniMapBitmap = miniMap
        )
        assertEquals(800, renderedTemplate2.width)
        assertEquals(600, renderedTemplate2.height)
    }
}
