package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.LocationSource

@Entity(tableName = "stamped_photos")
data class StampedPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreUri: String,
    val localFilePath: String,
    val displayTimestamp: String,
    val exifDateTimeOriginal: String,
    val timeZoneLabel: String,
    val isManualTime: Boolean,
    val fullAddress: String,
    val decimalCoordinates: String,
    val dmsCoordinates: String,
    val latitude: Double,
    val longitude: Double,
    val locationSource: String = LocationSource.GPS,
    val plusCode: String = "",
    val magneticFieldUt: Float = 45.72f,
    val altitudeMeters: Double,
    val azimuthDegrees: Float,
    val cardinalDirection: String,
    val templateName: String,
    val projectNote: String,
    val savedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_location_profile")
data class SavedLocationProfileEntity(
    @PrimaryKey val id: Int = 1,
    val isManualLocation: Boolean,
    val locationSource: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val plusCode: String,
    val street: String,
    val subLocality: String,
    val locality: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String,
    val showCountryFlag: Boolean,
    val altitudeMeters: Double,
    val projectNote: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
