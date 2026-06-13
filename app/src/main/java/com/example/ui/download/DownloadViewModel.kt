package com.example.ui.download

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ProDownloaderApp
import com.example.data.download.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * الـ ViewModel المسؤول عن معالجة وإدارة منطق واجهة المستخدم (MVVM) لتطبيق التنزيلات.
 * يدمج بين سجل لقاعدة البيانات والتدفق اللحظي للسرعة والتقدم النشط.
 */
class DownloadViewModel(
    private val app: Application,
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager
) : AndroidViewModel(app) {

    private val TAG = "DownloadViewModel"

    // دمج تدفقات التنزيلات الدائمة بالـ SQLite مع التدفق العابر لنسب التقدم بالـ RAM
    val downloads: StateFlow<List<DownloadItem>> = combine(
        downloadDao.getAllDownloads(),
        downloadManager.downloadProgressFlow
    ) { dbList, progressMap ->
        dbList.map { dbItem ->
            // إذا كان الملف قيد التحميل الآن، نأخذه من التدفق اللحظي المحتوي على السرعة والـ ETA الحاليين
            progressMap[dbItem.id] ?: dbItem
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // حالات الإدخال والخطأ لواجهة الإضافة
    private val _isResolving = MutableStateFlow(false)
    val isResolving = _isResolving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * وظيفة إضافة وإدراج تنزيل جديد مع جميع الخيارات المخصصة
     */
    fun addDownloadWithDetails(
        url: String, 
        fileName: String, 
        folderPath: String, 
        threadCount: Int, 
        fileType: FileType,
        fileSize: Long = 0L
    ) {
        if (url.isEmpty() || !url.startsWith("http")) {
            _errorMessage.value = "الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https."
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            clearError()
            try {
                val folderFile = File(folderPath)
                if (!folderFile.exists()) {
                    folderFile.mkdirs()
                }
                val finalFile = File(folderFile, fileName)

                val newDownload = DownloadItem(
                    fileName = fileName,
                    url = url,
                    filePath = finalFile.absolutePath,
                    fileSize = fileSize,
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED,
                    fileType = fileType,
                    createdAt = System.currentTimeMillis(),
                    mimeType = "application/octet-stream",
                    threadCount = threadCount
                )

                val newId = downloadDao.insert(newDownload)
                Log.d(TAG, "تم إدراج التحميل مخصص بنجاح بـ معرّف: $newId")

                DownloadService.startService(app, DownloadService.ACTION_START, newId)

            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة سجل التحميل المخصص: ${e.message}"
                Log.e(TAG, "خطأ في إضافة سجل تحميل مخصص: ${e.message}")
            } finally {
                _isResolving.value = false
            }
        }
    }

    /**
     * حل استطلاع شامل للرابط والحجم ونوع الملف من الويب
     */
    fun resolveFileInfoDetailed(url: String, onFinished: (fileName: String, fileSize: Long, fileType: FileType) -> Unit, onFailure: (String) -> Unit) {
        if (url.isEmpty() || !url.startsWith("http")) {
            onFailure("الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https.")
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            clearError()
            try {
                val (fileName, fileType) = downloadManager.resolveFileInfo(url)
                var determinedLength = 0L
                try {
                    val client = okhttp3.OkHttpClient.Builder().build()
                    val req = okhttp3.Request.Builder().url(url).head().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            determinedLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "فصل جلب الحجم بالـ Head: ${e.message}")
                }
                onFinished(fileName, determinedLength, fileType)
            } catch (e: Exception) {
                onFailure("فشل قراءة تفاصيل الرابط: ${e.message}")
            } finally {
                _isResolving.value = false
            }
        }
    }

    /**
     * إيقاف متعدد للتنزيلات النشطة
     */
    fun pauseMultiple(ids: List<Long>) {
        ids.forEach { id ->
            DownloadService.startService(app, DownloadService.ACTION_PAUSE, id)
        }
    }

    /**
     * استئناف متعدد للتنزيلات المؤوقة أو المعطلة
     */
    fun resumeMultiple(ids: List<Long>) {
        ids.forEach { id ->
            DownloadService.startService(app, DownloadService.ACTION_RESUME, id)
        }
    }

    /**
     * حذف تفاعلي متعدد
     */
    fun deleteMultiple(items: List<DownloadItem>) {
        items.forEach { item ->
            downloadManager.deleteDownload(item)
        }
    }

    /**
     * وظيفة إضافة وإدراج تنزيل جديد
     */
    fun addDownload(url: String, threadCount: Int) {
        if (url.isEmpty() || !url.startsWith("http")) {
            _errorMessage.value = "الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https."
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            clearError()
            try {
                // استخلاص تفاصيل الملف وعنوانه ونوعه من الويب
                val (fileName, fileType) = downloadManager.resolveFileInfo(url)

                // تحديد مسار حفظ الملف في الدليل العام للتنزيلات أو مسار التطبيق الخارجي الآمن
                val downloadDir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: app.filesDir
                val finalFile = File(downloadDir, fileName)

                // تجهيز العنصر في كائن التنزيلات
                val newDownload = DownloadItem(
                    fileName = fileName,
                    url = url,
                    filePath = finalFile.absolutePath,
                    fileSize = 0L, // سيتم تحديثه تلقائياً بواسطة محرك التنزيل
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED,
                    fileType = fileType,
                    createdAt = System.currentTimeMillis(),
                    mimeType = "application/octet-stream",
                    threadCount = threadCount
                )

                // إدراج السجل في قاعدة البيانات للحصول على المعرّف (ID)
                val newId = downloadDao.insert(newDownload)

                Log.d(TAG, "تم إدراج التحميل بنجاح في قاعدة البيانات بـ معرّف: $newId")

                // تشغيل خدمة الخلفية Foreground Service للبدء الفعلي بالتحميل
                DownloadService.startService(app, DownloadService.ACTION_START, newId)

            } catch (e: Exception) {
                _errorMessage.value = "فشل التعرف على تفاصيل الملف: ${e.message}"
                Log.e(TAG, "خطأ في إضافة سجل التحميل: ${e.message}")
            } finally {
                _isResolving.value = false
            }
        }
    }

    /**
     * إيقاف مؤقت للتنزيل
     */
    fun pauseDownload(id: Long) {
        DownloadService.startService(app, DownloadService.ACTION_PAUSE, id)
    }

    /**
     * استئناف التنزيل
     */
    fun resumeDownload(id: Long) {
        DownloadService.startService(app, DownloadService.ACTION_RESUME, id)
    }

    /**
     * إلغاء التنزيل
     */
    fun cancelDownload(id: Long) {
        DownloadService.startService(app, DownloadService.ACTION_CANCEL, id)
    }

    /**
     * حذف التنزيل بالكامل من قاعدة البيانات والقرص
     */
    fun deleteDownload(item: DownloadItem) {
        downloadManager.deleteDownload(item)
    }

    /**
     * مستنع المصنع (ViewModel Factory) الموصى به لإدخال الكتل البرمجية بسلاسة دون مسببات للتعارض
     */
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ProDownloaderApp
                return DownloadViewModel(
                    application,
                    application.downloadDao,
                    application.downloadManager
                ) as T
            }
        }
    }
}
