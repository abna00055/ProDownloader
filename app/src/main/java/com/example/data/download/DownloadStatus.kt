package com.example.data.download

/**
 * يمثل حالة عملية التنزيل في التطبيق.
 */
enum class DownloadStatus {
    QUEUED,      // في قائمة الانتظار للتحميل
    DOWNLOADING, // قيد التحميل الآن
    PAUSED,      // تم الإيقاف المؤقت
    COMPLETED,   // مكتمل بنجاح
    FAILED,      // فشل التنزيل بسبب خطأ
    CANCELLED    // ملغى من قبل المستخدم
}
