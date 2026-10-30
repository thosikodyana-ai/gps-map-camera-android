package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.model.LocationMetadata
import com.example.model.TimeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class SavedPhotoResult(
    val mediaStoreUri: String,
    val localFilePath: String,
    val exifDateTimeOriginal: String
)

data class ExifInspectionReport(
    val dateTimeOriginal: String,
    val offsetTimeOriginal: String,
    val gpsLatitude: String,
    val gpsLongitude: String,
    val gpsAltitude: String,
    val imageDescription: String,
    val dimensions: String
)

object MediaStoreExifSaver {

    suspend fun saveStampedPhotoWithExif(
        context: Context,
        stampedBitmap: Bitmap,
        timeConfig: TimeConfig,
        location: LocationMetadata
    ): Result<SavedPhotoResult> = withContext(Dispatchers.IO) {
        runCatching {
            val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date(timeConfig.effectiveEpochMillis))
            val fileName = "GPSMap_${fileStamp}_${System.currentTimeMillis() % 1000}.jpg"

            // 1. Save a local copy inside app storage for fast offline gallery & FileProvider sharing
            val localDir = File(context.filesDir, "stamped_photos").apply { mkdirs() }
            val localFile = File(localDir, fileName)
            FileOutputStream(localFile).use { out ->
                stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            writeExifTagsToFile(localFile.absolutePath, timeConfig, location)

            // 2. Save to Android MediaStore (DCIM/GPSMapCamera)
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, timeConfig.effectiveEpochMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DCIM}/GPSMapCamera"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collection, contentValues)
            if (insertedUri != null) {
                // Copy the local file (which already has EXIF headers embedded) directly into MediaStore stream
                resolver.openOutputStream(insertedUri)?.use { outStream ->
                    localFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                // Also write via ParcelFileDescriptor to guarantee MediaStore EXIF indexing
                runCatching {
                    resolver.openFileDescriptor(insertedUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        applyExifAttributes(exif, timeConfig, location)
                        exif.saveAttributes()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, publishValues, null, null)
                }
            }

            SavedPhotoResult(
                mediaStoreUri = insertedUri?.toString() ?: Uri.fromFile(localFile).toString(),
                localFilePath = localFile.absolutePath,
                exifDateTimeOriginal = "${timeConfig.formatExifDateTime()} (${timeConfig.timeZoneOption.exifOffset})"
            )
        }
    }

    private fun writeExifTagsToFile(
        filePath: String,
        timeConfig: TimeConfig,
        location: LocationMetadata
    ) {
        val exif = ExifInterface(filePath)
        applyExifAttributes(exif, timeConfig, location)
        exif.saveAttributes()
    }

    private fun applyExifAttributes(
        exif: ExifInterface,
        timeConfig: TimeConfig,
        location: LocationMetadata
    ) {
        val exifDateStr = timeConfig.formatExifDateTime()
        val offsetStr = timeConfig.timeZoneOption.exifOffset

        exif.setAttribute(ExifInterface.TAG_DATETIME, exifDateStr)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateStr)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, exifDateStr)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetStr)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetStr)
        exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offsetStr)

        // GPS Latitude & Longitude in rational DMS format ("dd/1,mm/1,ssss/100")
        exif.setAttribute(
            ExifInterface.TAG_GPS_LATITUDE,
            decimalToRationalDms(location.latitude)
        )
        exif.setAttribute(
            ExifInterface.TAG_GPS_LATITUDE_REF,
            if (location.latitude >= 0) "N" else "S"
        )
        exif.setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE,
            decimalToRationalDms(location.longitude)
        )
        exif.setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            if (location.longitude >= 0) "E" else "W"
        )

        val altAbs = (abs(location.altitudeMeters) * 100).toInt()
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "$altAbs/100")
        exif.setAttribute(
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            if (location.altitudeMeters >= 0) "0" else "1"
        )

        val azimuthRational = ((location.azimuthDegrees % 360f) * 100).toInt()
        exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "$azimuthRational/100")
        exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")

        val summaryDesc = "${location.fullAddress} | ${location.decimalCoordinatesText} | ${location.projectNote}"
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, summaryDesc)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "GPS Map Camera Android")
    }

    private fun decimalToRationalDms(coord: Double): String {
        val absolute = abs(coord)
        val degrees = absolute.toInt()
        val minutesFull = (absolute - degrees) * 60.0
        val minutes = minutesFull.toInt()
        val secondsHundredths = ((minutesFull - minutes) * 60.0 * 1000.0).toInt()
        return "$degrees/1,$minutes/1,$secondsHundredths/1000"
    }

    suspend fun inspectExifFromFile(localFilePath: String): ExifInspectionReport =
        withContext(Dispatchers.IO) {
            runCatching {
                val exif = ExifInterface(localFilePath)
                val latLong = exif.latLong
                ExifInspectionReport(
                    dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "-",
                    offsetTimeOriginal = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: "+07:00",
                    gpsLatitude = latLong?.getOrNull(0)?.let { String.format(Locale.US, "%.6f", it) }
                        ?: (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) ?: "-"),
                    gpsLongitude = latLong?.getOrNull(1)?.let { String.format(Locale.US, "%.6f", it) }
                        ?: (exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) ?: "-"),
                    gpsAltitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) ?: "-",
                    imageDescription = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION) ?: "-",
                    dimensions = "${exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)} × ${exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)} px"
                )
            }.getOrDefault(
                ExifInspectionReport("-", "-", "-", "-", "-", "-", "-")
            )
        }

    suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Pair<Bitmap?, Pair<Double, Double>?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                var rotation = 0f
                var exifLatLon: Pair<Double, Double>? = null

                resolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    exif.latLong?.let { arr ->
                        if (arr.size >= 2) {
                            exifLatLon = Pair(arr[0], arr[1])
                        }
                    }
                }

                val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@withContext Pair(null, null)

                val oriented = if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                } else {
                    rawBitmap
                }

                // Cap max dimension to 2048px to avoid OOM while keeping sharp high-res output
                val maxDim = maxOf(oriented.width, oriented.height)
                val finalBmp = if (maxDim > 2048) {
                    val scale = 2048f / maxDim
                    Bitmap.createScaledBitmap(
                        oriented,
                        (oriented.width * scale).toInt().coerceAtLeast(1),
                        (oriented.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    oriented
                }

                Pair(finalBmp, exifLatLon)
            }.getOrDefault(Pair(null, null))
        }

    fun sharePhotoFile(context: Context, localFilePath: String, caption: String) {
        runCatching {
            val file = File(localFilePath)
            if (!file.exists()) return
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Bagikan Foto GPS Stamp")
            )
        }
    }
}
