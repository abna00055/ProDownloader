package com.example

import android.app.Application
import com.example.data.download.DownloadDatabase
import com.example.data.download.DownloadManager

/**
 * الكلاس الرئيسي للتطبيق (Application Class).
 * يقوم بتهيئة وحفظ الكائنات الأحادية (Singletons) لقاعدة البيانات ومحرك التنزيل.
 */
class ProDownloaderApp : Application() {

    // تهيئة كائن قاعدة البيانات بنمط اللايزي (Lazy Initialization)
    val database: DownloadDatabase by lazy {
        DownloadDatabase.getDatabase(this)
    }

    // تهيئة واجهة التحكم بالبيانات بنطاق التطبيق
    val downloadDao by lazy {
        database.downloadDao()
    }

    // تهيئة محرك التحميل المركزي بالتطبيق وإرسال نسخة الـ DAO له
    val downloadManager by lazy {
        DownloadManager(this, downloadDao)
    }

    override fun onCreate() {
        super.onCreate()
        // تهيئة محركات التحليل والدمج الصوتي والفيديو المتطور
        com.example.youtubedl.YoutubeDL.getInstance().init(this)
        com.example.youtubedl.FFmpeg.getInstance().init(this)
    }
}
