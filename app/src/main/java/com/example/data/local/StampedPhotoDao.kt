package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StampedPhotoDao {
    @Query("SELECT * FROM stamped_photos ORDER BY savedAtMillis DESC")
    fun getAllPhotos(): Flow<List<StampedPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: StampedPhotoEntity): Long

    @Query("DELETE FROM stamped_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("DELETE FROM stamped_photos")
    suspend fun clearAllPhotos()

    @Query("SELECT * FROM saved_location_profile WHERE id = 1 LIMIT 1")
    fun observeSavedLocationProfile(): Flow<SavedLocationProfileEntity?>

    @Query("SELECT * FROM saved_location_profile WHERE id = 1 LIMIT 1")
    suspend fun getSavedLocationProfileOnce(): SavedLocationProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocationProfile(profile: SavedLocationProfileEntity)
}
