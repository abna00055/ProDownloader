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
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

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

    // رابط تحميل مسبق التجهيز (للـ Share Intent ومستكشف الفيديو)
    private val _prefilledUrlFlow = MutableStateFlow<String?>(null)
    val prefilledUrlFlow = _prefilledUrlFlow.asStateFlow()

    private val _prefilledCookieFlow = MutableStateFlow<String?>(null)
    val prefilledCookieFlow = _prefilledCookieFlow.asStateFlow()

    private val _prefilledUserAgentFlow = MutableStateFlow<String?>(null)
    val prefilledUserAgentFlow = _prefilledUserAgentFlow.asStateFlow()

    fun triggerPrefilledUrl(url: String, cookie: String? = null, userAgent: String? = null) {
        _prefilledUrlFlow.value = url
        _prefilledCookieFlow.value = cookie
        _prefilledUserAgentFlow.value = userAgent
    }

    fun clearPrefilledUrl() {
        _prefilledUrlFlow.value = null
        _prefilledCookieFlow.value = null
        _prefilledUserAgentFlow.value = null
    }

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
        fileSize: Long = 0L,
        cookie: String? = null,
        userAgent: String? = null
    ) {
        if (url.isEmpty() || !url.startsWith("http")) {
            _errorMessage.value = "الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https."
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            clearError()
            try {
                val finalFilePath = if (folderPath.startsWith("content://")) {
                    try {
                        val treeUri = Uri.parse(folderPath)
                        val pickedDir = DocumentFile.fromTreeUri(app, treeUri)
                        val newFile = pickedDir?.createFile("application/octet-stream", fileName)
                        newFile?.uri?.toString() ?: (folderPath + "/" + fileName)
                    } catch (e: Exception) {
                        Log.e(TAG, "فشل إنشاء الملف باستخدام SAF، استخدام مسار احتياطي", e)
                        File(folderPath, fileName).absolutePath
                    }
                } else {
                    val folderFile = File(folderPath)
                    if (!folderFile.exists()) {
                        folderFile.mkdirs()
                    }
                    File(folderFile, fileName).absolutePath
                }

                val newDownload = DownloadItem(
                    fileName = fileName,
                    url = url,
                    filePath = finalFilePath,
                    fileSize = fileSize,
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED,
                    fileType = fileType,
                    createdAt = System.currentTimeMillis(),
                    mimeType = "application/octet-stream",
                    threadCount = threadCount,
                    cookie = cookie,
                    userAgent = userAgent
                )

                val newId = downloadDao.insert(newDownload)
                Log.d(TAG, "تم إدراج التحميل مخصص بنجاح بـ معرّف: $newId")

                try {
                    DownloadService.startService(app, DownloadService.ACTION_START, newId)
                } catch (serviceException: Exception) {
                    Log.e("DownloadManager", "فشل بدء خدمة التحميل رقم: $newId في الخلفية", serviceException)
                    val failedItem = newDownload.copy(
                        id = newId, 
                        status = DownloadStatus.FAILED, 
                        errorMessage = "فشل بدء الخدمة: ${serviceException.localizedMessage}"
                    )
                    downloadDao.update(failedItem)
                }

            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة سجل التحميل المخصص: ${e.message}"
                Log.e("DownloadManager", "خطأ في إضافة سجل تحميل مخصص: ${e.message}", e)
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
    fun addDownload(url: String, threadCount: Int, cookie: String? = null, userAgent: String? = null) {
        if (url.isEmpty() || !url.startsWith("http")) {
            _errorMessage.value = "الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https."
            return
        }

        viewModelScope.launch {
            _isResolving.value = true
            clearError()
            try {
                // 1. استخدام اسم ملف مخمن مبدئياً لتسجيله فوراً في القائمة
                val guessedName = downloadManager.guessNameFromUrl(url)
                val guessedFileType = if (guessedName.lowercase().endsWith(".mp3") || guessedName.lowercase().endsWith(".wav")) FileType.AUDIO else FileType.VIDEO

                // 2. قراءة مسار التنزيلات الافتراضي من Preferences DataStore
                val settingsManager = com.example.data.settings.SettingsManager.getInstance(app)
                val defaultDir = settingsManager.defaultDownloadPathFlow.first()

                val finalFilePath = if (defaultDir.startsWith("content://")) {
                    try {
                        val treeUri = Uri.parse(defaultDir)
                        val pickedDir = DocumentFile.fromTreeUri(app, treeUri)
                        val newFile = pickedDir?.createFile("application/octet-stream", guessedName)
                        newFile?.uri?.toString() ?: (defaultDir + "/" + guessedName)
                    } catch (e: Exception) {
                        Log.e(TAG, "فشل إنشاء الملف باستخدام SAF في addDownload، استخدام الاحتياطي", e)
                        File(defaultDir, guessedName).absolutePath
                    }
                } else {
                    val folderFile = File(defaultDir)
                    if (!folderFile.exists()) {
                        folderFile.mkdirs()
                    }
                    File(folderFile, guessedName).absolutePath
                }

                // 3. تجهيز العنصر فوراً بحالة QUEUED لعرضه في الواجهة دون تأخير
                val newDownload = DownloadItem(
                    fileName = guessedName,
                    url = url,
                    filePath = finalFilePath,
                    fileSize = 0L,
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED,
                    fileType = guessedFileType,
                    createdAt = System.currentTimeMillis(),
                    mimeType = "application/octet-stream",
                    threadCount = threadCount,
                    cookie = cookie,
                    userAgent = userAgent
                )

                // إدراج السجل في قاعدة البيانات فوراً للحصول على المعرّف (ID)
                val newId = downloadDao.insert(newDownload)
                Log.d(TAG, "تم إدراج التحميل QUEUED فوراً بنجاح بـ معرّف: $newId")

                // 4. تشغيل خدمة الخلفية Foreground Service للبدء الفعلي بالتحميل
                try {
                    DownloadService.startService(app, DownloadService.ACTION_START, newId)
                } catch (serviceException: Exception) {
                    Log.e("DownloadManager", "فشل بدء خدمة الخلفية للتحميل رقم $newId", serviceException)
                    val failedItem = newDownload.copy(
                        id = newId, 
                        status = DownloadStatus.FAILED, 
                        errorMessage = "فشل بدء خدمة التحميل: ${serviceException.localizedMessage}"
                    )
                    downloadDao.update(failedItem)
                }

            } catch (e: Exception) {
                _errorMessage.value = "فشل إضافة سجل التحميل: ${e.message}"
                Log.e("DownloadManager", "خطأ في إضافة سجل التحميل المباشر: ${e.message}", e)
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
     * HLS Parser for parsing M3U8 Master Playlists to extract available stream qualities.
     */
    suspend fun parseM3u8MasterPlaylist(masterUrl: String): List<M3u8StreamOption> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val options = mutableListOf<M3u8StreamOption>()
        try {
            val client = okhttp3.OkHttpClient.Builder().build()
            val request = okhttp3.Request.Builder().url(masterUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val content = response.body?.string() ?: return@withContext emptyList()
                val lines = content.lineSequence().map { it.trim() }.toList()

                var i = 0
                val size = lines.size
                while (i < size) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF:")) {
                        // Extract BANDWIDTH
                        val bandwidthMatch = "BANDWIDTH=(\\d+)".toRegex().find(line)
                        val bandwidth = bandwidthMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                        // Extract RESOLUTION
                        val resolutionMatch = "RESOLUTION=([\\dxX]+)".toRegex().find(line)
                        val resolution = resolutionMatch?.groupValues?.get(1) ?: ""

                        // Look for raw stream URL on the next non-empty lines that don't start with '#'
                        var nextLineIdx = i + 1
                        var streamUrl = ""
                        while (nextLineIdx < size) {
                            val potentialUrl = lines[nextLineIdx]
                            if (potentialUrl.isNotEmpty() && !potentialUrl.startsWith("#")) {
                                streamUrl = potentialUrl
                                break
                            }
                            nextLineIdx++
                        }

                        if (streamUrl.isNotEmpty()) {
                            val absoluteUrl = resolveUrl(masterUrl, streamUrl)
                            val resLabel = if (resolution.isNotEmpty()) {
                                val height = resolution.substringAfter('x', "").trim()
                                if (height.isNotEmpty()) "${height}p" else resolution
                            } else {
                                "جودة تلقائية"
                            }
                            val mbps = if (bandwidth > 0) String.format("%.2f Mbps", bandwidth / 1000000.0) else ""
                            val label = if (mbps.isNotEmpty()) "$resLabel ($mbps)" else resLabel

                            options.add(
                                M3u8StreamOption(
                                    resolution = resolution.ifEmpty { "unknown" },
                                    bandwidth = bandwidth,
                                    url = absoluteUrl,
                                    label = label
                                )
                            )
                        }
                    }
                    i++
                }
            }
        } catch (e: Exception) {
            Log.e("M3u8Parser", "Error parsing master playlist: ${e.message}", e)
        }

        // Fallback: If no sub-qualities were parsed, the URL might be a direct Media Playlist (chunklist).
        if (options.isEmpty()) {
            options.add(
                M3u8StreamOption(
                    resolution = "معيارية",
                    bandwidth = 0L,
                    url = masterUrl,
                    label = "البث المباشر المتاح (افتراضي)"
                )
            )
        }

        return@withContext options.sortedByDescending { it.bandwidth }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl
        }
        return try {
            val bUrl = java.net.URL(baseUrl)
            java.net.URL(bUrl, relativeUrl).toString()
        } catch (e: Exception) {
            val lastSlash = baseUrl.lastIndexOf('/')
            if (lastSlash != -1) {
                baseUrl.substring(0, lastSlash + 1) + relativeUrl
            } else {
                relativeUrl
            }
        }
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

/**
 * خيارات البث المتاحة لروابط HLS/M3U8
 */
data class M3u8StreamOption(
    val resolution: String,
    val bandwidth: Long,
    val url: String,
    val label: String
)

