package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.io.File

class PhotoHistoryRepository(private val dao: StampedPhotoDao) {
    val allPhotos: Flow<List<StampedPhotoEntity>> = dao.getAllPhotos()
    val savedLocationProfile: Flow<SavedLocationProfileEntity?> = dao.observeSavedLocationProfile()

    suspend fun insertPhoto(photo: StampedPhotoEntity): Long {
        return dao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: StampedPhotoEntity) {
        runCatching {
            val file = File(photo.localFilePath)
            if (file.exists()) {
                file.delete()
            }
        }
        dao.deletePhotoById(photo.id)
    }

    suspend fun getSavedLocationProfileOnce(): SavedLocationProfileEntity? {
        return dao.getSavedLocationProfileOnce()
    }

    suspend fun saveLocationProfile(profile: SavedLocationProfileEntity) {
        dao.saveLocationProfile(profile)
    }
}
