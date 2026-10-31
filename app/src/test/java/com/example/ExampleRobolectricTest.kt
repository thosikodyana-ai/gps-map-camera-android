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

    @Test
    fun testA_selectNewManualMapLocationAndConfirmOk_savesNewLocationWithoutRevertingToOld() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CameraStampViewModel(application)

        // Initial location A (Brebes)
        assertEquals(-6.887405, viewModel.locationState.value.latitude, 1e-5)
        assertEquals(109.035938, viewModel.locationState.value.longitude, 1e-5)

        // User selects new location B on map (Kramat, Tegal) and presses "Pilih Lokasi (OK)"
        val newLatB = -6.885461
        val newLonB = 109.206151
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = newLatB,
            longitude = newLonB,
            altitudeMeters = 12.0,
            plusCode = "4674+rf3",
            street = "Latu, Kertayasa",
            subLocality = "Kertayasa",
            locality = "Kec. Kramat",
            city = "Kabupaten Tegal",
            province = "Jawa Tengah",
            postalCode = "52181",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Captured by GPS Map Camera",
            customFullAddressOverride = ""
        )

        val stateAfterOk = viewModel.locationState.value
        assertTrue(stateAfterOk.isManualLocation)
        assertEquals(LocationSource.MANUAL_MAP, stateAfterOk.locationSource)
        assertEquals(newLatB, stateAfterOk.latitude, 1e-6)
        assertEquals(newLonB, stateAfterOk.longitude, 1e-6)
        assertEquals("Kec. Kramat", stateAfterOk.locality)
        assertEquals("Kabupaten Tegal", stateAfterOk.city)
        assertTrue(stateAfterOk.template1HeaderLine1.contains("Kec. Kramat"))
        assertTrue(!stateAfterOk.template1HeaderLine1.contains("Brebes"))
    }

    @Test
    fun testB_and_testE_reopenMapAndRecreateState_loadsLastSavedManualLocationFromRoomDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = PhotoHistoryRepository(db.stampedPhotoDao())

        // Save location B
        val savedB = SavedLocationProfileEntity(
            id = 1,
            isManualLocation = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -6.885461,
            longitude = 109.206151,
            address = "Latu, Kertayasa, Kec. Kramat, Kabupaten Tegal, Jawa Tengah 52181, Indonesia",
            plusCode = "4674+rf3",
            street = "Latu, Kertayasa",
            subLocality = "Kertayasa",
            locality = "Kec. Kramat",
            city = "Kabupaten Tegal",
            province = "Jawa Tengah",
            postalCode = "52181",
            country = "Indonesia",
            showCountryFlag = true,
            altitudeMeters = 12.0,
            projectNote = "Captured by GPS Map Camera"
        )
        repository.saveLocationProfile(savedB)

        // Reopen / reload saved location profile
        val reloaded = repository.getSavedLocationProfileOnce()
        assertNotNull(reloaded)
        assertEquals(-6.885461, reloaded!!.latitude, 1e-6)
        assertEquals(109.206151, reloaded.longitude, 1e-6)
        assertEquals("Kec. Kramat", reloaded.locality)
        assertEquals(LocationSource.MANUAL_MAP, reloaded.locationSource)
        db.close()
    }

    @Test
    fun testC_sequentialManualLocationUpdates_B_to_C_to_D_alwaysReflectsLatestSelection() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CameraStampViewModel(application)

        // Step 1: Location B (Kramat, Tegal)
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -6.885461,
            longitude = 109.206151,
            altitudeMeters = 10.0,
            plusCode = "",
            street = "Kertayasa",
            subLocality = "",
            locality = "Kec. Kramat",
            city = "Kabupaten Tegal",
            province = "Jawa Tengah",
            postalCode = "52181",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Note B"
        )
        assertEquals(-6.885461, viewModel.locationState.value.latitude, 1e-6)
        assertEquals("Kec. Kramat", viewModel.locationState.value.locality)

        // Step 2: Location C (Slawi)
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -6.978292,
            longitude = 109.140945,
            altitudeMeters = 25.0,
            plusCode = "",
            street = "Procot",
            subLocality = "",
            locality = "Kecamatan Slawi",
            city = "Kabupaten Tegal",
            province = "Jawa Tengah",
            postalCode = "52411",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Note C"
        )
        assertEquals(-6.978292, viewModel.locationState.value.latitude, 1e-6)
        assertEquals("Kecamatan Slawi", viewModel.locationState.value.locality)

        // Step 3: Location D (Semarang) — even when confirmed with only customFullAddressOverride before geocoding finishes
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = -6.966667,
            longitude = 110.416664,
            altitudeMeters = 15.0,
            plusCode = "",
            street = "",
            subLocality = "",
            locality = "",
            city = "",
            province = "",
            postalCode = "",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Note D",
            customFullAddressOverride = "Jl. Pemuda, Sekayu, Kec. Semarang Tengah, Kota Semarang, Jawa Tengah, Indonesia"
        )
        val stateD = viewModel.locationState.value
        assertEquals(-6.966667, stateD.latitude, 1e-6)
        assertEquals(110.416664, stateD.longitude, 1e-6)
        assertTrue(!stateD.fullAddress.contains("Slawi"))
        assertTrue(!stateD.fullAddress.contains("Brebes"))
        assertTrue(stateD.fullAddress.contains("Semarang"))
    }

    @Test
    fun testD_cancelManualLocationSelection_retainsPreviouslySavedLocationD() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CameraStampViewModel(application)

        // Confirm Location D
        val savedLatD = -6.966667
        val savedLonD = 110.416664
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = savedLatD,
            longitude = savedLonD,
            altitudeMeters = 15.0,
            plusCode = "2cq8+8m",
            street = "Jl. Pemuda",
            subLocality = "Sekayu",
            locality = "Kec. Semarang Tengah",
            city = "Kota Semarang",
            province = "Jawa Tengah",
            postalCode = "50132",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Semarang"
        )

        // User opens map picker, pans draft coordinates to E (-7.795580, 110.369490), and clicks "Batal" (onDismissCancel)
        // Because onDismissCancel does NOT invoke setManualLocationData, the ViewModel state must remain D
        val current = viewModel.locationState.value
        assertEquals(savedLatD, current.latitude, 1e-6)
        assertEquals(savedLonD, current.longitude, 1e-6)
        assertEquals("Kota Semarang", current.city)
    }

    @Test
    fun testF_invalidOrOutOfRangeCoordinates_areSafelySanitizedAndClamped() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CameraStampViewModel(application)

        // Pass NaN / out-of-range coordinates
        viewModel.setManualLocationData(
            isManual = true,
            locationSource = LocationSource.MANUAL_MAP,
            latitude = Double.NaN,
            longitude = 250.0,
            altitudeMeters = Double.POSITIVE_INFINITY,
            plusCode = "",
            street = "Test Street",
            subLocality = "",
            locality = "Test Locality",
            city = "Test City",
            province = "Test Province",
            postalCode = "",
            country = "Indonesia",
            showCountryFlag = true,
            projectNote = "Test"
        )

        val sanitized = viewModel.locationState.value
        assertTrue(!sanitized.latitude.isNaN())
        assertTrue(sanitized.latitude in -90.0..90.0)
        assertEquals(180.0, sanitized.longitude, 1e-6)
        assertEquals(0.0, sanitized.altitudeMeters, 1e-6)
    }
}
