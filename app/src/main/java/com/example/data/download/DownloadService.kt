package com.example.data.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.ProDownloaderApp
import kotlinx.coroutines.*
import java.io.File

/**
 * خدمة في المقدمة (Foreground Service) لإدارة التنزيلات في الخلفية وضمان ثباتها.
 * تعرض كرت إشعار تفاعلي مستمر يحتوي على نسب التقدم وسرعة التحميل وزري إيقاف وإلغاء.
 */
class DownloadService : Service() {

    private val TAG = "DownloadService"

    // موجهات الكوروتين لإدارة المهام في الخلفية
    private val serviceScope = CoroutineScope(DispatchContexts.IO + SupervisorJob())
    private var progressTrackingJob: Job? = null

    // معرفات الإشعارات والقنوات
    private val CHANNEL_ID = "pro_downloader_channel"
    private val COMPLETE_CHANNEL_ID = "pro_downloader_complete_channel"
    private val FOREGROUND_NOTIFICATION_ID = 9182

    companion object {
        // إجراءات الإشعارات (Notification Actions)
        const val ACTION_START = "com.example.action.START"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_CANCEL = "com.example.action.CANCEL"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        /**
         * دالة مساعدة لسهولة تشغيل الخدمة مع إرسال الأوامر
         */
        fun startService(context: Context, action: String, downloadId: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "تم إنشاء خدمة التنزيل Foreground Service")
        createNotificationChannels()
        startForegroundWithNotification()
        observeActiveDownloads()

        // استدعاء إشعار الاكتمال فور نجاح تحميل أي ملف
        val app = application as ProDownloaderApp
        app.downloadManager.onDownloadCompletedListener = { item ->
            showCompletionNotification(item)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L

        Log.d(TAG, "منفّذ الأوامر استقبل: Action = $action | ID = $downloadId")

        if (downloadId != -1L) {
            val app = application as ProDownloaderApp
            val downloadManager = app.downloadManager

            when (action) {
                ACTION_START -> {
                    serviceScope.launch {
                        val dbItem = app.downloadDao.getDownloadById(downloadId)
                        if (dbItem != null) {
                            downloadManager.startDownload(dbItem)
                        }
                    }
                }
                ACTION_PAUSE -> {
                    downloadManager.pauseDownload(downloadId)
                }
                ACTION_RESUME -> {
                    serviceScope.launch {
                        val dbItem = app.downloadDao.getDownloadById(downloadId)
                        if (dbItem != null) {
                            downloadManager.startDownload(dbItem)
                        }
                    }
                }
                ACTION_CANCEL -> {
                    downloadManager.cancelDownload(downloadId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "تدمير خدمة الخلفية Foreground Service")
        serviceScope.cancel()
        progressTrackingJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * تشغيل الخدمة في المقدمة مع إشعار مبدئي خالٍ لتجنب انهيار التطبيق
     */
    private fun startForegroundWithNotification() {
        val notification = createNotificationBuilder(
            title = "Pro Downloader",
            text = "قيد الاستعداد للتنزيل...",
            progress = 0,
            hasProgress = false
        ).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    /**
     * مراقبة تدفق عمليات التنزيل الجارية وجمع معلوماتها للتحديث المنسق كل ثانية
     */
    private fun observeActiveDownloads() {
        val app = application as ProDownloaderApp
        val downloadManager = app.downloadManager

        progressTrackingJob?.cancel()
        progressTrackingJob = serviceScope.launch {
            // سنراقب التنزيلات النشطة وقاعدة البيانات لتعديل الإشعار المستمر
            combineFlowsAndNotify(app, downloadManager)
        }
    }

    private suspend fun combineFlowsAndNotify(app: ProDownloaderApp, downloadManager: DownloadManager) {
        // نقوم بمراقبة الـ ProgressFlow بشكل مستمر
        downloadManager.downloadProgressFlow.collect { progressMap ->
            val activeList = progressMap.values.toList()

            if (activeList.isEmpty()) {
                // إذا لم يكن هنالك أي تنزيل نشط، سنعرض إشعاراً ثابتاً بسيطاً أو نوقف التشغيل في المقدمة
                updatePersistentNotification("لا توجد تحمّيلات نشطة الآن", 0, false, 0L, 0)
                return@collect
            }

            // حساب المعطيات التراكمية للتنزيلات الجارية
            var totalBytes: Long = 0
            var downloadedBytes: Long = 0
            var totalSpeed: Long = 0
            val activeCount = activeList.size

            activeList.forEach { item ->
                if (item.fileSize > 0) {
                    totalBytes += item.fileSize
                    downloadedBytes += item.downloadedBytes
                }
                totalSpeed += item.speed
            }

            val overallPercent = if (totalBytes > 0) {
                ((downloadedBytes * 100) / totalBytes).toInt()
            } else {
                0
            }

            // تحديث الإشعار المستمر بالاحصاءات الإجمالية وتجاوب فوري لأول عنصر جاري تشغيله لتحكم أسرع
            val firstActive = activeList.firstOrNull()
            updatePersistentNotification(
                message = "تنزيل $activeCount ملفات | التقدم: $overallPercent%",
                progress = overallPercent,
                hasProgress = totalBytes > 0,
                speedBytes = totalSpeed,
                activeCount = activeCount,
                firstActiveItem = firstActive
            )

            // في حال وجود تنزيلات اكتملت للتو، سنقوم بإطلاق إشعار منفصل "اكتمل التنزيل" لها
            // يتم ذلك عبر التمحيص الإجرائي عند المراقبة الذاكرية
        }
    }

    /**
     * تحديث محتوى الإشعار المستمر في شريط المهام مع دعم أزرار التحكم
     */
    private fun updatePersistentNotification(
        message: String,
        progress: Int,
        hasProgress: Boolean,
        speedBytes: Long,
        activeCount: Int,
        firstActiveItem: DownloadItem? = null
    ) {
        val speedText = formatSpeed(speedBytes)
        val titleText = if (activeCount > 0) "جاري التحميل بسرعه ($speedText)..." else "برو داونلودر"

        val builder = createNotificationBuilder(
            title = titleText,
            text = message,
            progress = progress,
            hasProgress = hasProgress
        )

        // إذا كان هناك عنصر نشط حالي، نعرض أزرار الإيقاف والتحكم خصيصاً له داخل الإشعار لسهولة الاستخدام!
        if (firstActiveItem != null) {
            builder.setSubText(firstActiveItem.fileName)

            // زر إيقاف مؤقت
            val pauseIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, firstActiveItem.id)
            }
            val pendingPause = PendingIntent.getService(
                this,
                firstActiveItem.id.toInt() + 10,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "إيقاف مؤقت", pendingPause)

            // زر إلغاء التنزيل
            val cancelIntent = Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, firstActiveItem.id)
            }
            val pendingCancel = PendingIntent.getService(
                this,
                firstActiveItem.id.toInt() + 20,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "إلغاء", pendingCancel)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, builder.build())
    }

    /**
     * إطلاق إشعار منفصل تفاعلي مستقل "اكتمل التنزيل"
     */
    fun showCompletionNotification(item: DownloadItem) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val file = File(item.filePath)
        if (!file.exists()) {
            Log.e(TAG, "الملف لم يتم العثور عليه لعرض إشعار الاكتمال.")
            return
        }

        // تحضير الـ URI الآمن عن طريق FileProvider
        val fileUri: Uri = try {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "فشل إنشاء URI للمشاركة: ${e.message}")
            Uri.fromFile(file)
        }

        // إنشاء نية لفتح الملف بالبرامج المتاحة بالنظام
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            item.id.toInt() + 100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // إنشاء نية لمشاركة الملف مباشرة مع منصات أخرى
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, "مشاركة الملف عبر:")
        val pendingShare = PendingIntent.getActivity(
            this,
            item.id.toInt() + 200,
            chooserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // بناء كرت إشعار الاكتمال
        val builder = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("اكتمل التنزيل بنجاح! 🎉")
            .setContentText(item.fileName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen) // النقر على الإشعار يفتح الملف تلقائياً
            .addAction(android.R.drawable.ic_menu_view, "فتح الملف", pendingOpen)
            .addAction(android.R.drawable.ic_menu_share, "مشاركة", pendingShare)

        notificationManager.notify((item.id + 1000).toInt(), builder.build())
    }

    /**
     * تكوين منشئ كائنات الإشعار بشكل معياري متناسق لتقليل كتابة الأكواد المكررة
     */
    private fun createNotificationBuilder(
        title: String,
        text: String,
        progress: Int,
        hasProgress: Boolean
    ): NotificationCompat.Builder {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true) // لا تزعج المستخدم بالصوت كل ثانية مع كل تقدم متوالي
            .apply {
                if (hasProgress) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(0, 0, false)
                }
            }
    }

    /**
     * إنشاء قنوات الإشعارات اللازمة لأندرويد 8.0 فما فوق
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // قناة للتنزيلات المستمرة
            val progressChannel = NotificationChannel(
                CHANNEL_ID,
                "سرعة وتقدم التنزيلات",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "عرض حالة عمليات التحميل الجارية في الخلفية بالوقت الفعلي."
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(progressChannel)

            // قناة للملفات المكتملة
            val completeChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "ملفات مكتملة التحميل",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات فورية عند اكتمال تحميل أي ملف بنجاح."
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(completeChannel)
        }
    }

    /**
     * تنسيق حجم البايتات المستلمة بذكاء لعرض السرعة بالـ KB/s أو MB/s
     */
    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> {
                String.format("%.2f MB/ث", bytesPerSec.toDouble() / (1024 * 1024))
            }
            bytesPerSec >= 1024 -> {
                String.format("%.1f KB/ث", bytesPerSec.toDouble() / 1024)
            }
            else -> {
                "$bytesPerSec بايت/ث"
            }
        }
    }
}
