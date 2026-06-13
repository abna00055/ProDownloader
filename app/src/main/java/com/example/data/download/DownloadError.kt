package com.example.data.download

/**
 * يمثل الفئات المختلفة لأخطاء التحميل المدعومة برسائل تفصيلية باللغة العربية ومحسنة للاستخدام والنشر.
 */
sealed class DownloadError {
    object NoNetwork : DownloadError()
    object InvalidLink : DownloadError() // 404/410
    object RefusedConnection : DownloadError() // 401/403
    object Timeout : DownloadError()
    data class InsufficientSpace(val requiredBytes: Long, val availableBytes: Long) : DownloadError()
    data class Unknown(val message: String) : DownloadError()

    fun toUserMessage(): String {
        return when (this) {
            is NoNetwork -> "لا يوجد اتصال بالإنترنت! يرجى التحقق من الشبكة ومحاولة الاتصال بالـ Wi-Fi أو بيانات الجوال."
            is InvalidLink -> "الرابط المدخل غير صالح أو منتهي الصلاحية (HTTP 404/410). يرجى التحقق من الرابط وإعادة المحاولة."
            is RefusedConnection -> "يرفض خادم الويب الاتصال بالرابط (HTTP 401/403). قد يتطلب التحميل إذناً خاصاً أو تسجيل دخول."
            is Timeout -> "انتهت مهلة الاتصال بالخادم. سيقوم التطبيق بإعادة المحاولة تلقائياً فور تحسن استجابة الشبكة."
            is InsufficientSpace -> {
                val reqMb = requiredBytes / (1024 * 1024)
                val avMb = availableBytes / (1024 * 1024)
                "المساحة المتاحة على جهازك غير كافية للتنزيل! المطلوب: ${reqMb} ميجابايت، المتوفر: ${avMb} ميجابايت."
            }
            is Unknown -> "حدث خطأ غير متوقع: $message"
        }
    }
}
