package com.example.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * قاعدة بيانات التطبيق لتخزين سجل وتفاصيل التحميلات.
 */
@Database(entities = [DownloadItem::class], version = 1, exportSchema = false)
@TypeConverters(DownloadConverters::class)
abstract class DownloadDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        fun getDatabase(context: Context): DownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "pro_downloader_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
