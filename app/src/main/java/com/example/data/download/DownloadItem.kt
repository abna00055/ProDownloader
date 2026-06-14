package com.example.data.download

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * يمثل سجل ملف تنزيل مخزن في قاعدة البيانات.
 */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val url: String,
    val filePath: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val fileType: FileType,
    val createdAt: Long = System.currentTimeMillis(),
    val mimeType: String,
    val threadCount: Int = 4,
    val errorMessage: String? = null,
    val cookie: String? = null,
    val userAgent: String? = null
) {
    // سرعة التحميل اللحظية (بالبايت في الثانية) - لا يتم حفظها في قاعدة البيانات ولكنه يحسب في الذاكرة أثناء النشاط
    @Ignore
    var speed: Long = 0L

    // الوقت المتبقي المتوقع للتحميل بالثواني - مؤقت ومحسوب ديناميكياً
    @Ignore
    var etaSeconds: Long = -1L
}
