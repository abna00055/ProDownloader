package com.example.data.download

import androidx.room.TypeConverter

/**
 * محولات نوع البيانات لقاعدة بيانات Room لتخزين واسترداد هياكل البيانات المخصصة (Enums).
 */
class DownloadConverters {

    @TypeConverter
    fun fromStatus(status: DownloadStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(statusStr: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(statusStr)
        } catch (e: Exception) {
            DownloadStatus.QUEUED
        }
    }

    @TypeConverter
    fun fromFileType(type: FileType): String {
        return type.name
    }

    @TypeConverter
    fun toFileType(typeStr: String): FileType {
        return try {
            FileType.valueOf(typeStr)
        } catch (e: Exception) {
            FileType.OTHER
        }
    }
}
