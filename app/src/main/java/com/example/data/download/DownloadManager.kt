package com.example.data.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import com.example.widget.DownloadWidgetProvider

/**
 * محرك التنزيل الرئيسي (Download Manager Engine) المسؤول عن:
 * 1. تقسيم الملف لأجزاء وتنزيلها متوازياً (Multi-threaded chunks).
 * 2. دعم استئناف التحميل (Resume) وإيقاف وإلغاء فوري.
 * 3. حساب سرعة التحميل اللحظية والوقت المتبقي ETA.
 * 4. التحقق من المساحة الفارغة.
 * 5. استخراج اسم الملف ونوعه.
 * 6. إعادة المحاولة التلقائية (Retry up to 3 times with exponential backoff).
 */
class DownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val TAG = "DownloadManager"

    private fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val info = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return info.type == ConnectivityManager.TYPE_WIFI
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return info.isConnected
        }
    }

    // وظائف العمل الفعالة لكل معرّف تنزيل (ID -> Coroutine Job)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /**
     * تخمين اسم ملف تقريبي من الرابط للتسهيل على المستخدم في التخزين الموضعي
     */
    fun guessNameFromUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            val path = uri.path
            if (path != null) {
                val lastSegment = path.substringAfterLast('/')
                if (lastSegment.isNotEmpty() && lastSegment.contains(".")) {
                    return lastSegment
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        val epoch = System.currentTimeMillis() % 100000
        return "download_file_$epoch.mp4"
    }

    // تدفق لحالات الأجهزة النشطة بالوقت الفعلي لتحديث الواجهة البرمجية بسرعة
    private val _downloadProgressFlow = MutableStateFlow<Map<Long, DownloadItem>>(emptyMap())
    val downloadProgressFlow = _downloadProgressFlow.asStateFlow()

    // نطاق كوروتين لإدارة العمليات في الخلفية بشكل مستقل عن الواجهة
    private val engineScope = CoroutineScope(DispatchContexts.IO + SupervisorJob())

    // مستمع عام عند اكتمال تنزيل أي ملف لإخطار الخدمة بعرض إشعار الاكتمال
    var onDownloadCompletedListener: ((DownloadItem) -> Unit)? = null

    /**
     * استخراج اسم الملف من الرابط أو الهيدرز
     */
    suspend fun resolveFileInfo(url: String, cookie: String? = null, userAgent: String? = null): Pair<String, FileType> = withContext(DispatchContexts.IO) {
        var fileName = "downloaded_file"
        var mimeType = ""

        try {
            // نقوم بإرسال طلب HEAD للحصول على معلومات الملف دون تحميل محتواه
            val requestBuilder = Request.Builder()
                .url(url)
            if (!cookie.isNullOrEmpty()) {
                requestBuilder.header("Cookie", cookie)
            }
            if (!userAgent.isNullOrEmpty()) {
                requestBuilder.header("User-Agent", userAgent)
            }
            requestBuilder.header("Referer", url)
            
            val request = requestBuilder.head().build()

            executeWithRetry {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        mimeType = response.header("Content-Type") ?: ""
                        val contentDisposition = response.header("Content-Disposition")
                        if (!contentDisposition.isNullOrEmpty()) {
                            fileName = extractFileNameFromDisposition(contentDisposition)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في الاتصال الاستطلاعي: ${e.message}")
        }

        // إذا لم نجد اسم الملف من الهيدر، نستخرجه من الرابط نفسه
        if (fileName == "downloaded_file") {
            fileName = extractFileNameFromUrl(url)
        }

        // تحديد نوع الملف بناءً على الامتداد أو الـ mimeType
        val fileType = parseFileType(fileName, mimeType)

        return@withContext Pair(fileName, fileType)
    }

    /**
     * بدء عملية التنزيل
     */
    fun startDownload(item: DownloadItem) {
        // تجنب تكرار التنزيل إذا كان فعالاً بالفعل
        if (activeJobs.containsKey(item.id)) {
            Log.d(TAG, "التنزيل ${item.id} قيد العمل بالفعل.")
            return
        }

        val job = engineScope.launch {
            try {
                // Check if wifiOnly setting is enabled
                val settingsManager = com.example.data.settings.SettingsManager.getInstance(context)
                val wifiOnly = settingsManager.wifiOnlyFlow.first()
                if (wifiOnly && !isWifiConnected(context)) {
                    updateItemStatus(item.copy(status = DownloadStatus.FAILED))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "التحميل متوقف: تم تفعيل خيار (التحميل عبر WiFi فقط) ولست متصلاً بـ WiFi حالياً.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // تصفية السجل وتعديل حالته إلى قيد التحميل
                updateItemStatus(item.copy(status = DownloadStatus.DOWNLOADING))
                Log.d(TAG, "بدء تشغيل مهمة التحميل لـ ID: ${item.id}")

                // إجراء التحميل الفعلي
                performDownload(item.id)
            } catch (c: CancellationException) {
                Log.i(TAG, "تم إلغاء أو إيقاف التحميل ID: ${item.id} بنجاح.")
                // لا نحتاج لتغيير الحالة هنا لأن دالة pause/cancel ستقوم بذلك بشكل منسق
            } catch (e: Exception) {
                Log.e(TAG, "خطأ فادح في التحميل: ${e.message}", e)
                updateItemStatus(item.copy(status = DownloadStatus.FAILED))
            } finally {
                activeJobs.remove(item.id)
                removeFromProgressFlow(item.id)
            }
        }

        activeJobs[item.id] = job
    }

    /**
     * إيقاف التنزيل مؤقتاً
     */
    fun pauseDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        removeFromProgressFlow(id)

        engineScope.launch {
            val dbItem = downloadDao.getDownloadById(id)
            if (dbItem != null && dbItem.status == DownloadStatus.DOWNLOADING) {
                updateItemStatus(dbItem.copy(status = DownloadStatus.PAUSED))
                Log.d(TAG, "تم بنجاح إيقاف التحميل ID: $id مؤقتاً.")
            }
        }
    }

    /**
     * إلغاء التنزيل نهائياً وحذف الملفات المؤقتة
     */
    fun cancelDownload(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        removeFromProgressFlow(id)

        engineScope.launch {
            val dbItem = downloadDao.getDownloadById(id)
            if (dbItem != null) {
                updateItemStatus(dbItem.copy(status = DownloadStatus.CANCELLED))

                // حذف الملف النهائي والملفات المؤقتة
                if (dbItem.filePath.startsWith("content://")) {
                    try {
                        val docUri = Uri.parse(dbItem.filePath)
                        val docFile = DocumentFile.fromSingleUri(context, docUri)
                        if (docFile != null && docFile.exists()) {
                            docFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "فصل حذف سجل التخزين الذكي SAF: ${e.message}")
                    }
                } else {
                    val finalFile = File(dbItem.filePath)
                    if (finalFile.exists()) finalFile.delete()
                }

                // حذف الأجزاء المؤقتة من مجلد الكاش الآمن
                for (i in 0 until dbItem.threadCount) {
                    val partFile = File(context.cacheDir, "download_${dbItem.id}_part_$i")
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                }
                Log.d(TAG, "تم إلغاء التنزيل وحذف الأجزاء والمستندات لـ ID: $id")
            }
        }
    }

    /**
     * إيقاف وحذف تنزيل معين من قاعدة البيانات بالكامل
     */
    fun deleteDownload(item: DownloadItem) {
        cancelDownload(item.id)
        engineScope.launch {
            downloadDao.delete(item)
        }
    }

    /**
     * التحميل الفعلي مجزأً أو كملف واحد
     */
    private suspend fun performDownload(id: Long) = withContext(DispatchContexts.IO) {
        var dbItem = downloadDao.getDownloadById(id) ?: return@withContext

        // 0. التحقق من اتصال الإنترنت العام
        if (!isNetworkConnected(context)) {
            val errorMsg = DownloadError.NoNetwork.toUserMessage()
            updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
            return@withContext
        }

        // 1. فحص معلومات الاتصال والحصول على الحجم الكلي ودعم Range
        val checkRequestBuilder = Request.Builder().url(dbItem.url)
        if (!dbItem.cookie.isNullOrEmpty()) {
            checkRequestBuilder.addHeader("Cookie", dbItem.cookie)
        }
        if (!dbItem.userAgent.isNullOrEmpty()) {
            checkRequestBuilder.addHeader("User-Agent", dbItem.userAgent)
        }
        checkRequestBuilder.addHeader("Referer", dbItem.url)
        val checkRequest = checkRequestBuilder.head().build()
        var totalLength = dbItem.fileSize
        var supportsRange = false
        var mimeType = dbItem.mimeType

        try {
            executeWithRetry {
                okHttpClient.newCall(checkRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val lengthHeader = response.header("Content-Length")
                        if (lengthHeader != null) {
                            totalLength = lengthHeader.toLong()
                        }
                        mimeType = response.header("Content-Type") ?: dbItem.mimeType
                        val acceptRanges = response.header("Accept-Ranges")
                        supportsRange = acceptRanges == "bytes" || response.header("Content-Range") != null
                    } else {
                        when (response.code) {
                            404, 410 -> throw java.io.FileNotFoundException("Invalid link")
                            401, 403 -> throw java.security.AccessControlException("Refused connection")
                            else -> throw Exception("HTTP error code: ${response.code}")
                        }
                    }
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            val errorMsg = DownloadError.InvalidLink.toUserMessage()
            updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
            return@withContext
        } catch (e: java.security.AccessControlException) {
            val errorMsg = DownloadError.RefusedConnection.toUserMessage()
            updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
            return@withContext
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = DownloadError.Timeout.toUserMessage()
            updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
            return@withContext
        } catch (e: Exception) {
            Log.e(TAG, "فشل فحص الحجم، سنحاول التحميل المباشر: ${e.message}")
        }

        // تحديث قاعدة البيانات بالطول المستخرج وصيغة mimeType
        dbItem = dbItem.copy(fileSize = totalLength, mimeType = mimeType)
        updateItemStatus(dbItem)

        // 2. التحقق من المساحة المتوفرة قبل البدء
        if (totalLength > 0) {
            val isSaf = dbItem.filePath.startsWith("content://")
            val checkDir = if (isSaf) context.cacheDir else {
                val f = File(dbItem.filePath)
                f.parentFile ?: (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            }
            val stat = StatFs(checkDir.path)
            val availableSpace = stat.availableBytes
            if (availableSpace < totalLength) {
                val errorMsg = DownloadError.InsufficientSpace(totalLength, availableSpace).toUserMessage()
                Log.e(TAG, "لا توجد مساحة كافية على الجهاز!")
                updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
                return@withContext
            }
        }

        // إنشاء المجلد الأب إذا لم يكن مجلداً ذكياً SAF
        if (!dbItem.filePath.startsWith("content://")) {
            val finalFile = File(dbItem.filePath)
            finalFile.parentFile?.mkdirs()
        }

        // 3. اتخاذ قرار بالتقسيم أو التحميل بخيط واحد
        val finalThreadCount = if (supportsRange && totalLength > 0) dbItem.threadCount else 1
        Log.d(TAG, "وضع التنزيل: يدعم التقسيم = $supportsRange، عدد الخيوط = $finalThreadCount")

        // حساب التقسيمات
        val parts = mutableListOf<DownloadPart>()
        if (finalThreadCount > 1) {
            val partSize = totalLength / finalThreadCount
            for (i in 0 until finalThreadCount) {
                val start = i * partSize
                val end = if (i == finalThreadCount - 1) totalLength - 1 else (i + 1) * partSize - 1
                parts.add(DownloadPart(index = i, startOffset = start, endOffset = end))
            }
        } else {
            parts.add(DownloadPart(index = 0, startOffset = 0, endOffset = totalLength - 1))
        }

        // 4. إطلاق مهام التنزيل لكل جزء بالتوازي
        val partJobs = mutableListOf<Deferred<Boolean>>()
        val speedTracker = RealTimeSpeedTracker()

        coroutineScope {
            parts.forEach { part ->
                val deferred = async(DispatchContexts.IO) {
                    downloadPartWithRetry(dbItem, part, speedTracker)
                }
                partJobs.add(deferred)
            }

            // مراقب السرعة اللحظي وتحديث الـ Flow وقاعدة البيانات كل ثانية
            val speedMonitorJob = launch {
                while (isActive) {
                    delay(1000)
                    val speed = speedTracker.getInstantaneousSpeedAndReset()

                    // حساب التقدم الكلي بجمع أحجام ملفات الأجزاء المحملة فعلياً من الكاش
                    var totalDownloaded: Long = 0
                    parts.forEach { p ->
                        val partFile = File(context.cacheDir, "download_${dbItem.id}_part_${p.index}")
                        if (partFile.exists()) {
                            totalDownloaded += partFile.length()
                        }
                    }

                    // إذا انتهى حجم أحد الأجزاء واكتمل فجأة نضبطه
                    if (totalDownloaded > totalLength && totalLength > 0) {
                        totalDownloaded = totalLength
                    }

                    // حساب الـ ETA
                    val eta = if (speed > 0 && totalLength > 0) {
                        (totalLength - totalDownloaded) / speed
                    } else {
                        -1L
                    }

                    // تحديث الحالة التفاعلية الذاكرية لقراءتها في الواجهة
                    val updatedItem = dbItem.copy(
                        downloadedBytes = totalDownloaded,
                        status = DownloadStatus.DOWNLOADING
                    ).apply {
                        this.speed = speed
                        this.etaSeconds = eta
                    }

                    _downloadProgressFlow.value = _downloadProgressFlow.value.toMutableMap().apply {
                        put(id, updatedItem)
                    }

                    // تحديث دوري لقاعدة البيانات للتخزين الدائم
                    downloadDao.update(updatedItem)
                    DownloadWidgetProvider.updateAllWidgets(context)
                }
            }

            // انتظار انتهاء جميع الأجزاء
            val results = partJobs.awaitAll()
            speedMonitorJob.cancel() // إيقاف مراقبة السرعة بعد اكتمال التحميل

            // التحقق من نجاح تحميل كافة الأجزاء
            if (results.all { it }) {
                Log.d(TAG, "تحميل جميع الأجزاء تم بنجاح. بدء الدمج...")
                mergeParts(dbItem, finalThreadCount)
                val finalDownloadedBytes = if (totalLength > 0) totalLength else {
                    if (dbItem.filePath.startsWith("content://")) {
                        try {
                            val docUri = Uri.parse(dbItem.filePath)
                            val docFile = DocumentFile.fromSingleUri(context, docUri)
                            docFile?.length() ?: 0L
                        } catch (e: Exception) { 0L }
                    } else {
                        File(dbItem.filePath).length()
                    }
                }
                val completedItem = dbItem.copy(
                    downloadedBytes = finalDownloadedBytes,
                    status = DownloadStatus.COMPLETED,
                    errorMessage = null
                )
                updateItemStatus(completedItem)
                onDownloadCompletedListener?.invoke(completedItem)
                Log.d(TAG, "اكتمل التنزيل والدمج للملف: ${dbItem.fileName}")
            } else {
                Log.e(TAG, "فشلت واحدة أو أكثر من الأجزاء في التحميل.")
                val errorMsg = if (!isNetworkConnected(context)) {
                    DownloadError.NoNetwork.toUserMessage()
                } else {
                    DownloadError.Timeout.toUserMessage()
                }
                updateItemStatus(dbItem.copy(status = DownloadStatus.FAILED, errorMessage = errorMsg))
            }
        }
    }

    /**
     * تنزيل جزء محدد مع إعادة المحاولة التلقائية 3 مرات وتأخير تصاعدي
     */
    private suspend fun downloadPartWithRetry(
        dbItem: DownloadItem,
        part: DownloadPart,
        speedTracker: RealTimeSpeedTracker
    ): Boolean {
        var success = false
        var attempt = 1

        while (attempt <= 3 && !success) {
            try {
                coroutineContext.ensureActive()
                success = downloadPartInternal(dbItem, part, speedTracker)
                if (success) break
            } catch (c: CancellationException) {
                throw c // إعادة تمرير إلغاء الكوروتين الفوري
            } catch (e: Exception) {
                Log.e(TAG, "محاولة $attempt لفشل تحميل الجزء ${part.index}: ${e.message}")
                if (attempt == 3) {
                    return false
                }
                // تأخير تصاعدي (Exponential Backoff): 1ث، 2ث، 4ث
                val delayMs = (1000 * Math.pow(2.0, (attempt - 1).toDouble())).toLong()
                delay(delayMs)
                attempt++
            }
        }
        return success
    }

    /**
     * تنزيل الجزء الفعلي وكتابته بالقرص مع دعم الاستئناف (Resume)
     */
    private suspend fun downloadPartInternal(
        dbItem: DownloadItem,
        part: DownloadPart,
        speedTracker: RealTimeSpeedTracker
    ): Boolean = withContext(DispatchContexts.IO) {
        val partFile = File(context.cacheDir, "download_${dbItem.id}_part_${part.index}")
        var currentBytes = if (partFile.exists()) partFile.length() else 0L

        // التحقق من الاكتمال المسبق للجزء في حال استؤنف بعد نهايته
        val targetPartLength = if (part.endOffset >= 0) (part.endOffset - part.startOffset + 1) else -1
        if (targetPartLength > 0 && currentBytes >= targetPartLength) {
            Log.d(TAG, "الجزء ${part.index} مكتمل مسبقاً بطول: $currentBytes")
            return@withContext true
        }

        // بناء طلب النطاق في حال دعمه، والاستئناف من آخر بايت محمل للجوزء
        val requestBuilder = Request.Builder().url(dbItem.url)
        if (!dbItem.cookie.isNullOrEmpty()) {
            requestBuilder.addHeader("Cookie", dbItem.cookie)
        }
        if (!dbItem.userAgent.isNullOrEmpty()) {
            requestBuilder.addHeader("User-Agent", dbItem.userAgent)
        }
        requestBuilder.addHeader("Referer", dbItem.url)

        if (dbItem.fileSize > 0 && part.endOffset >= 0) {
            val rangeStart = part.startOffset + currentBytes
            val rangeEnd = part.endOffset
            if (rangeStart <= rangeEnd) {
                requestBuilder.header("Range", "bytes=$rangeStart-$rangeEnd")
                Log.d(TAG, "نطاق الطلب للجزء ${part.index}: Range=bytes=$rangeStart-$rangeEnd")
            }
        }

        val request = requestBuilder.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                Log.e(TAG, "السيرفر أرجع رمز فشل للجزء ${part.index}: ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(partFile, true) // وضع الإلحاق (Append) لـ Resume

            val buffer = ByteArray(8192)
            var bytesRead: Int

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    coroutineContext.ensureActive() // التحقق من الإيقاف الفوري
                    outputStream.write(buffer, 0, bytesRead)
                    speedTracker.addBytes(bytesRead.toLong())
                }
                outputStream.flush()
            } finally {
                try { outputStream.close() } catch (e: Exception) {}
                try { inputStream.close() } catch (e: Exception) {}
            }
        }

        // التحقق النهائي من حجم الملف عند الاكتمال
        if (targetPartLength > 0) {
            val finalPartLen = partFile.length()
            if (finalPartLen >= targetPartLength) {
                return@withContext true
            } else {
                Log.e(TAG, "تحذير: الملف المجزأ ${part.index} لم يحصل على كامل البيانات. الطول المنفذ: $finalPartLen من أصل $targetPartLength")
                return@withContext false
            }
        }

        return@withContext true
    }

    /**
     * دمج أجزاء الملف المؤقتة في الملف النهائي
     */
    private suspend fun mergeParts(dbItem: DownloadItem, threadCount: Int) = withContext(DispatchContexts.IO) {
        val isSaf = dbItem.filePath.startsWith("content://")
        if (isSaf) {
            try {
                val docUri = Uri.parse(dbItem.filePath)
                context.contentResolver.openOutputStream(docUri, "rwt")?.use { output ->
                    for (i in 0 until threadCount) {
                        val partFile = File(context.cacheDir, "download_${dbItem.id}_part_$i")
                        if (partFile.exists()) {
                            partFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                            partFile.delete() // حذف جزء الملف المؤقت بعد دمجه بنجاح
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "فشل دمج الأجزاء لملف SAF: ${e.message}", e)
                throw e
            }
        } else {
            val destFile = File(dbItem.filePath)
            if (destFile.exists()) {
                destFile.delete()
            }

            FileOutputStream(destFile).use { output ->
                for (i in 0 until threadCount) {
                    val partFile = File(context.cacheDir, "download_${dbItem.id}_part_$i")
                    if (partFile.exists()) {
                        partFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                        partFile.delete() // حذف جزء الملف المؤقت بعد دمجه بنجاح
                    }
                }
            }
        }
        Log.d(TAG, "تم دمج الملف بنجاح وحذف ملفات الأجزاء المؤقتة.")
    }

    /**
     * فحص توفر مساحة كافية على التخزين
     */
    private fun hasEnoughStorageSpace(requiredBytes: Long): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val stat = StatFs(dir.path)
            val availableSpace = stat.availableBytes
            availableSpace > requiredBytes
        } catch (e: Exception) {
            // كخيار وقائي إذا حدث استثناء أثناء فحص المساحة
            true
        }
    }

    /**
     * تحديث حالة التنزيل في قاعدة البيانات
     */
    private suspend fun updateItemStatus(item: DownloadItem) {
        downloadDao.update(item)
        DownloadWidgetProvider.updateAllWidgets(context)
    }

    private fun removeFromProgressFlow(id: Long) {
        val updatedMap = _downloadProgressFlow.value.toMutableMap()
        updatedMap.remove(id)
        _downloadProgressFlow.value = updatedMap
    }

    /**
     * مستخرج اسم الملف من هيدر Content-Disposition
     */
    private fun extractFileNameFromDisposition(disposition: String): String {
        try {
            val patternStr = "filename\\*=UTF-8''(.+)|filename=\"([^\"]+)\"|filename=(.+)"
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(disposition)
            if (matcher.find()) {
                val utf8File = matcher.group(1)
                val quotedFile = matcher.group(2)
                val rawFile = matcher.group(3)

                val rawName = utf8File ?: quotedFile ?: rawFile ?: ""
                var decoded = URLDecoder.decode(rawName, "UTF-8")
                // إزالة علامات الاقتباس أو الفراغات الزائدة إن وجدت
                decoded = decoded.replace("\"", "").trim()
                if (decoded.isNotEmpty()) return decoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في استخراج الاسم من الهيدر: ${e.message}")
        }
        return "downloaded_file"
    }

    /**
     * استخراج اسم الملف من الرابط
     */
    private fun extractFileNameFromUrl(url: String): String {
        try {
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            val lastSlash = decodedUrl.lastIndexOf('/')
            if (lastSlash != -1) {
                var name = decodedUrl.substring(lastSlash + 1)
                // إزالة البارامترات مثل ?query=params
                val questionMark = name.indexOf('?')
                if (questionMark != -1) {
                    name = name.substring(0, questionMark)
                }
                if (name.isNotEmpty()) {
                    return name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في استخراج الاسم من الرابط: ${e.message}")
        }
        return "downloaded_file_${System.currentTimeMillis()}"
    }

    /**
     * تصنيف نوع الملف بناءً على الاسم أو صيغة المايم
     */
    private fun parseFileType(fileName: String, mimeType: String): FileType {
        val nameLower = fileName.lowercase()
        val mimeLower = mimeType.lowercase()

        return when {
            // التحقق من الفيديو
            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
                    nameLower.endsWith(".webm") || nameLower.endsWith(".mov") || mimeLower.contains("video") -> {
                FileType.VIDEO
            }
            // التحقق من الصوت
            nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".aac") ||
                    nameLower.endsWith(".m4a") || nameLower.endsWith(".flac") || mimeLower.contains("audio") -> {
                FileType.AUDIO
            }
            // المستندات
            nameLower.endsWith(".pdf") || nameLower.endsWith(".doc") || nameLower.endsWith(".docx") ||
                    nameLower.endsWith(".txt") || nameLower.endsWith(".xls") || nameLower.endsWith(".xlsx") ||
                    nameLower.endsWith(".ppt") || nameLower.endsWith(".pptx") || mimeLower.contains("document") ||
                    mimeLower.contains("pdf") || mimeLower.contains("text") -> {
                FileType.DOCUMENT
            }
            // الأرشيفات المضغوطة
            nameLower.endsWith(".zip") || nameLower.endsWith(".rar") || nameLower.endsWith(".7z") ||
                    nameLower.endsWith(".tar") || nameLower.endsWith(".gz") || mimeLower.contains("zip") ||
                    mimeLower.contains("x-rar") || mimeLower.contains("compressed") -> {
                FileType.ARCHIVE
            }
            // تطبيقات اندرويد
            nameLower.endsWith(".apk") || mimeLower.contains("android.package-archive") -> {
                FileType.APK
            }
            else -> FileType.OTHER
        }
    }

    /**
     * تشغيل المهمة بوجود آلية المحاولة
     */
    private suspend inline fun executeWithRetry(crossinline block: () -> Unit) {
        var err: Exception? = null
        for (i in 1..3) {
            try {
                block()
                return
            } catch (e: Exception) {
                err = e
                delay(1000)
            }
        }
        if (err != null) throw err
    }
}

/**
 * فئة نموذجية للجزء المراد تحميله
 */
data class DownloadPart(
    val index: Int,
    val startOffset: Long,
    val endOffset: Long
)

/**
 * متتبع السرعة اللحظي الآمن للخيوط المتعددة
 */
class RealTimeSpeedTracker {
    private var bytesDownloadedThisSecond = java.util.concurrent.atomic.AtomicLong(0L)

    fun addBytes(bytes: Long) {
        bytesDownloadedThisSecond.addAndGet(bytes)
    }

    fun getInstantaneousSpeedAndReset(): Long {
        return bytesDownloadedThisSecond.getAndSet(0L)
    }
}

/**
 * مصلح ثوابت وموزعات الكوروتين المستقلة لضمان عثور العمليات عليها تحت Threading صحيح
 */
object DispatchContexts {
    val IO: CoroutineDispatcher = Dispatchers.IO
}
