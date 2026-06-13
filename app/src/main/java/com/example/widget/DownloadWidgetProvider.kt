package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.download.DownloadDatabase
import com.example.data.download.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * جهاز استقبال تحديثات الـ Widget (App Widget Provider) للشاشة الرئيسية.
 * يعرض عدد التنزيلات النشطة ونسبة التقدم الكلية بشكل تفاعلي بالتعاون مع قاعدة بيانات Room.
 */
class DownloadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = DownloadDatabase.getDatabase(context)
                val activeDownloads = db.downloadDao().getActiveDownloadsSync()
                
                withContext(Dispatchers.Main) {
                    for (appWidgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, appWidgetId, activeDownloads)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        /**
         * تحديث فوري لجميع نسخ الويدجت النشطة على الشاشة الرئيسية.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, DownloadWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = DownloadDatabase.getDatabase(context)
                    val activeDownloads = db.downloadDao().getActiveDownloadsSync()
                    
                    withContext(Dispatchers.Main) {
                        for (appWidgetId in appWidgetIds) {
                            updateWidget(context, appWidgetManager, appWidgetId, activeDownloads)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            activeDownloads: List<DownloadItem>
        ) {
            val views = RemoteViews(context.packageName, R.layout.download_widget)

            if (activeDownloads.isEmpty()) {
                views.setTextViewText(R.id.widget_active_count, "لا توجد تنزيلات نشطة حالياً")
                views.setViewVisibility(R.id.widget_progress_container, View.GONE)
            } else {
                val count = activeDownloads.size
                views.setTextViewText(R.id.widget_active_count, "جاري تنزيل عدد $count من الملفات")
                views.setViewVisibility(R.id.widget_progress_container, View.VISIBLE)

                // حساب التقدم الكلي التراكمي لجميع الملفات
                var totalBytes: Long = 0
                var downloadedBytes: Long = 0
                for (item in activeDownloads) {
                    // تجنب جمع الأطوال الافتراضية الصفرية
                    if (item.fileSize > 0) {
                        totalBytes += item.fileSize
                        downloadedBytes += item.downloadedBytes
                    } else {
                        // كفرز افتراضي لتجنب الصفر المطلق
                        totalBytes += 100
                        downloadedBytes += 0
                    }
                }

                val progressPercent = if (totalBytes > 0) {
                    ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                views.setTextViewText(R.id.widget_progress_percent, "$progressPercent%")
                views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
