package com.example.data.download

/**
 * يمثل نوع الملف الذي يتم تنزيله لتصنيفه وعرض أيقونة مناسبة له.
 */
enum class FileType {
    VIDEO,      // مقطع فيديو
    AUDIO,      // ملف صوتي
    DOCUMENT,   // مستندات (PDF, Doc, text...)
    ARCHIVE,    // ملفات مضغوطة (ZIP, RAR...)
    APK,        // تطبيقات أندرويد
    OTHER       // ملفات أخرى
}
