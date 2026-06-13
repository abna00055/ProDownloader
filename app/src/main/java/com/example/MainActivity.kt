package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.download.DownloadScreen
import com.example.ui.download.DownloadViewModel
import com.example.ui.theme.MyApplicationTheme

/**
 * النشاط الرئيسي للتطبيق (Main Entry Activity).
 * يقوم بتمكين عرض الحواف الكاملة للشاشة (Edge-to-Edge) واستدعاء لوحة التنزيلات.
 */
class MainActivity : ComponentActivity() {

    // إنشاء كائن الـ ViewModel بتمرير المصنع (Factory) لحقن التبعيات بشكل آمن
    private val viewModel: DownloadViewModel by viewModels { DownloadViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تمكين واجهات العرض من الحافة إلى الحافة بذكاء
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                DownloadScreen(viewModel = viewModel)
            }
        }
    }
}

