package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * مساعد لإدارة وتصدير الملفات المحملة إلى وحدات التخزين العامة الدائمة (Public Persistent Storage)
 * لضمان عدم حذفها عند إلغاء تثبيت التطبيق، مع تنظيمها تلقائياً بالاعتماد على MediaStore و File API.
 */
object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"
    private const val SUB_DIR_NAME = "ProDownloader"

    // ثوابت المجلدات والمسارات لضمان ثباتها وعدم السماح بتغييرها بالخطأ
    const val FOLDER_VIDEOS = "Movies/$SUB_DIR_NAME"
    const val FOLDER_AUDIOS = "Music/$SUB_DIR_NAME"
    const val FOLDER_DOWNLOADS = "Download/$SUB_DIR_NAME"

    /**
     * حفظ الملف المكتمل من مسار مؤقت (مثل Cache) غلى التخزين المستمر العام للجهاز.
     * @return مسار الملف النهائي (Content URI كـ String لأندرويد 10+ أو مسار الملف الحقيقي لأندرويد 9-)
     */
    fun saveFileToPublicStorage(
        context: Context,
        tempFile: File,
        fileName: String,
        fileType: FileType,
        mimeType: String
    ): String? {
        if (!tempFile.exists()) {
            Log.e(TAG, "الملف المؤقت غير موجود لتصديره للقرص الدائم!")
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveUsingMediaStore(context, tempFile, fileName, fileType, mimeType)
        } else {
            saveUsingLegacyStorage(context, tempFile, fileName, fileType)
        }
    }

    /**
     * الحفظ باستخدام MediaStore API لأنظمة Android 10+ (API 29+) لمنع فقدان الملفات وحذفها وضمان ظهورها الفوري.
     */
    private fun saveUsingMediaStore(
        context: Context,
        tempFile: File,
        fileName: String,
        fileType: FileType,
        mimeType: String
    ): String? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // منع ظهور الملف أثناء الكتابة الجارية
        }

        val collectionUri = when (fileType) {
            FileType.VIDEO -> {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_VIDEOS)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            FileType.AUDIO -> {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_AUDIOS)
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            else -> {
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER_DOWNLOADS)
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
        }

        var itemUri: Uri? = null
        try {
            itemUri = resolver.insert(collectionUri, contentValues)
            if (itemUri == null) {
                Log.e(TAG, "فشل إدراج سجل جديد للملف في MediaStore")
                return null
            }

            // كتابة بايتات الملف الفعلي إلى المجرى التابع لـ Uri
            resolver.openOutputStream(itemUri, "w")?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // تحديث حالة الملف لإزالة Pending ليتم رصده بكافة التطبيقات الأخرى فوراً
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)

            Log.i(TAG, "تم بنجاح تصدير الملف لـ MediaStore: $itemUri")
            return itemUri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "فشل تصدير الملف عبر MediaStore لـ $fileName: ${e.message}", e)
            if (itemUri != null) {
                try {
                    resolver.delete(itemUri, null, null)
                } catch (ex: Exception) {
                    Log.e(TAG, "فشل تنظيف السجل المكسور بعد الخطأ: ${ex.message}")
                }
            }
            return null
        }
    }

    /**
     * الحفظ باستخدام مسارات البيئة العامة التقليدية لأنظمة Android 9 وأقل.
     */
    private fun saveUsingLegacyStorage(
        context: Context,
        tempFile: File,
        fileName: String,
        fileType: FileType
    ): String? {
        val publicDirType = when (fileType) {
            FileType.VIDEO -> Environment.DIRECTORY_MOVIES
            FileType.AUDIO -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }

        val basePublicDir = Environment.getExternalStoragePublicDirectory(publicDirType)
        val targetFolder = File(basePublicDir, SUB_DIR_NAME)
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val finalFile = getUniqueFile(targetFolder, fileName)

        try {
            FileInputStream(tempFile).use { input ->
                FileOutputStream(finalFile).use { output ->
                    input.copyTo(output)
                }
            }

            // مسح الملف عبر الـ MediaScanner لضمان فهرسته وظهوره لجميع التطبيقات الأخرى فوراً
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalFile.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "تم مسح الملف بنجاح وتجهيزه: $path -> $uri")
            }

            Log.i(TAG, "تم بنجاح حفظ الملف التقليدي للتخزين العام: ${finalFile.absolutePath}")
            return finalFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "فشل حفظ الملف التقليدي بصيغة legacy لـ $fileName: ${e.message}", e)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            return null
        }
    }

    /**
     * توليد اسم ملف فريد لتجنب استبدال ملفات بنفس الاسم بأجهزة أندرويد القديمة.
     */
    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
        var count = 1

        while (file.exists()) {
            file = File(directory, "$nameWithoutExt ($count)$extSuffix")
            count++
        }
        return file
    }
}
