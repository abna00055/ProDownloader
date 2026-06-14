package com.example.data.download

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * مدير ومنظم الأذونات الخاص بالوصول إلى وسائط وتخزين الأجهزة الخارجية.
 * يتضمن الضمانات وثوابت إصدارات أندرويد لعدم المساس بها أو تخطي التحقق.
 */
object PermissionHandler {

    /**
     * التحقق الشامل والدقيق مما إذا كان التطبيق يمتلك الإذن الكافي للكتابة والقراءة.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return when {
            // Android 13+ (API 33+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val videoGranted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED

                val audioGranted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                videoGranted && audioGranted
            }
            // Android 11 & 12 (API 30-32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // التحقق الشامل من صلاحيات الوصول التام أو إذن الكش المتبقي
                Environment.isExternalStorageManager() || ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            // Android 10 و أقدم
            else -> {
                val writeGranted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                val readGranted = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                writeGranted && readGranted
            }
        }
    }

    /**
     * الحصول على مصفوفة الأذونات اللازمة للوصول وعرض الخيارات حسب بنية النظام.
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * فتح صفحة تفاصيل إعدادات التطبيق لتمكين إذن التخزين يدوياً في حالة الرفض الصارم.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // صمام أمان أخير لمنع الانهيار
            }
        }
    }
}
