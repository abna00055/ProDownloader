package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.main.MainContainerScreen
import com.example.ui.download.DownloadViewModel
import com.example.ui.theme.MyApplicationTheme

/**
 * النشاط الرئيسي للتطبيق (Main Entry Activity).
 * يقوم بتمكين عرض الحواف الكاملة للشاشة (Edge-to-Edge) واستدعاء الهيكل الأساسي والملاحة.
 * كما يعالج استقبال ومشاركة الروابط الخارجية (Share Intents) وتوجيهها لواجهات الإنزال الفوري.
 */
class MainActivity : ComponentActivity() {

    // إنشاء كائن الـ ViewModel بتمرير المصنع (Factory) لحقن التبعيات بشكل آمن
    private val viewModel: DownloadViewModel by viewModels { DownloadViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تمكين واجهات العرض من الحافة إلى الحافة بذكاء
        enableEdgeToEdge()

        // معالجة الحدث الممرر عند بدء وتدشين التطبيق لأول مرة مع رابط خارجي
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainContainerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * تحليل وفك ترميز البيانات الممررة من أحداث النظام وقنوات المشاركة الخارجية
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                if (sharedText.isNotEmpty()) {
                    // فك ومعاينة الكود لاستكشاف الرابط من منتصف النصوص المصاحبة
                    val extractedUrl = extractUrlFromText(sharedText)
                    if (extractedUrl.isNotEmpty()) {
                        viewModel.triggerPrefilledUrl(extractedUrl)
                    }
                }
            }
        } else if (Intent.ACTION_VIEW == action) {
            val clickedUrl = intent.dataString
            if (!clickedUrl.isNullOrEmpty()) {
                viewModel.triggerPrefilledUrl(clickedUrl)
            }
        }
    }

    /**
     * فرز واستخلاص الروابط المشفرة أو العادية من النصوص والرسائل المشاركة
     */
    private fun extractUrlFromText(text: String): String {
        return text.split("\\s+".toRegex()).firstOrNull { 
            it.startsWith("http://") || it.startsWith("https://") 
        } ?: ""
    }
}

