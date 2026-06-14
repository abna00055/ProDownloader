package com.example.ui.settings

import android.os.Environment
import android.widget.Toast
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import android.net.Uri
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * شاشة الإعدادات المتكاملة (SettingsScreen):
 * تدعم الاتصال الكامل بـ DataStore Preferences وحلقات منطق التحميل والتنظيم والتخصيص البصري.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val contentResolver = context.contentResolver

    // لاقط المجلدات SAF Tree بطلب صلاحيات الكتابة الدائمة للقرص
    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // حفظ الصلاحية الدائمة (Persistable URI Permission) للكتابة لاحقاً دون طلب متكرر
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                scope.launch {
                    settingsManager.setDefaultDownloadPath(uri.toString())
                }
                Toast.makeText(context, "تم تحديد مجلد الحفظ بنجاح وتفعيل رخصة الكتابة المستمرة ✅", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                scope.launch {
                    settingsManager.setDefaultDownloadPath(uri.toString())
                }
                Log.e("SettingsScreen", "Failed to take persistable URI permission, saving raw URI string", e)
            }
        }
    }

    // Checking storage permission status in Settings
    var hasStoragePermission by remember {
        mutableStateOf(com.example.data.download.PermissionHandler.hasStoragePermission(context))
    }

    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasStoragePermission = com.example.data.download.PermissionHandler.hasStoragePermission(context)
    }

    val settingsLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(settingsLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = com.example.data.download.PermissionHandler.hasStoragePermission(context)
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            settingsLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // قراءة الحالات اللحظية من DataStore
    val threadCount by settingsManager.threadCountFlow.collectAsState(initial = 4)
    val maxConcurrentDownloads by settingsManager.maxConcurrentDownloadsFlow.collectAsState(initial = 3)
    val autoStartOnOpen by settingsManager.autoStartOnOpenFlow.collectAsState(initial = false)
    val wifiOnly by settingsManager.wifiOnlyFlow.collectAsState(initial = false)
    val defaultDownloadPath by settingsManager.defaultDownloadPathFlow.collectAsState(initial = "")
    val autoOrganizeFiles by settingsManager.autoOrganizeFilesFlow.collectAsState(initial = true)
    
    val notifActive by settingsManager.notifActiveFlow.collectAsState(initial = true)
    val notifCompleted by settingsManager.notifCompletedFlow.collectAsState(initial = true)
    val notifFailed by settingsManager.notifFailedFlow.collectAsState(initial = true)
    val notifSound by settingsManager.notifSoundFlow.collectAsState(initial = true)
    val notifVibrate by settingsManager.notifVibrateFlow.collectAsState(initial = true)
    
    val themeMode by settingsManager.themeModeFlow.collectAsState(initial = "System")
    val accentColorHex by settingsManager.accentColorHexFlow.collectAsState(initial = 0xFF8B5CF6.toInt())

    // حساب حجم التخزين المستخدم للتطبيق بشكل موازٍ ومستمر
    var appStorageBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        appStorageBytes = settingsManager.getAppUsedStorageBytes()
    }

    // حالات محلية للـ Dialogs
    var showPathEditDialog by remember { mutableStateOf(false) }
    var inputPathText by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // الألوان المتوفرة لمحدد اللون الدائري (Circular Color Picker)
    val availableAccentColors = listOf(
        Color(0xFF8B5CF6), // البنفسجي الأساسي
        Color(0xFF3B82F6), // الأزرق
        Color(0xFF10B981), // الأخضر
        Color(0xFFEF4444), // الأحمر الغامق
        Color(0xFFF59E0B), // البرتقالي الدافئ
        Color(0xFFEC4899), // الوردي العصري
        Color(0xFF14B8A6)  // المائي التركوازي
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "إعدادات Pro Downloader",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // أ) قسم "التحميل العام"
            SettingsSectionHeader(title = "التحميل العام", icon = Icons.Default.CloudDownload)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // عدد الخيوط لتجزئة الملف
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("عدد Threads الافتراضي لكل تحميل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$threadCount قنوات اتصال",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(accentColorHex)
                            )
                        }
                        Slider(
                            value = threadCount.toFloat(),
                            onValueChange = { 
                                scope.launch { settingsManager.setThreadCount(it.toInt()) }
                            },
                            valueRange = 1f..8f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(accentColorHex),
                                thumbColor = Color(accentColorHex)
                            )
                        )
                        Text(
                            text = "تجزئة الملف لقنوات بث متعددة تعزز السرعة وتتجاوز قيود الخادم لقنص الفيديوهات.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // الحد الأقصى للتحميلات المتزامنة (Stepper)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("الحد الأقصى للتحميلات المتزامنة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("الحد الأقصى للملفات النشطة في وقت واحد", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (maxConcurrentDownloads > 1) {
                                        scope.launch { settingsManager.setMaxConcurrentDownloads(maxConcurrentDownloads - 1) }
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "تقليل", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "$maxConcurrentDownloads",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(accentColorHex)
                            )
                            IconButton(
                                onClick = {
                                    if (maxConcurrentDownloads < 5) {
                                        scope.launch { settingsManager.setMaxConcurrentDownloads(maxConcurrentDownloads + 1) }
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "زيادة", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // بدء التحميل تلقائياً عند فتح رابط
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("بدء التحميل تلقائياً عند فتح رابط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تخطي شاشة فحص الرابط والتفاصيل والبدء السريع", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoStartOnOpen,
                            onCheckedChange = { value ->
                                scope.launch { settingsManager.setAutoStartOnOpen(value) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(accentColorHex))
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // التحميل عبر WiFi فقط
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التحميل عبر WiFi فقط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تجنب استهلاك باقة بيانات الجوال وإيقاف التحميل فور الانقطاع", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { value ->
                                scope.launch { settingsManager.setWifiOnly(value) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(accentColorHex))
                        )
                    }
                }
            }

            // ب) قسم "التخزين"
            SettingsSectionHeader(title = "التخزين والمساحة", icon = Icons.Default.Storage)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // اختيار مسار الحفظ
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("مسار الحفظ الافتراضي العام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .clickable {
                                    folderPickerLauncher.launch(null)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val readablePath = if (defaultDownloadPath.startsWith("content://")) {
                                try {
                                    val decoded = Uri.decode(defaultDownloadPath)
                                    val treeIndex = decoded.indexOf("/tree/")
                                    if (treeIndex != -1) {
                                        val suffix = decoded.substring(treeIndex + 6)
                                        "رخصة مجلد SAF: $suffix"
                                    } else {
                                        "مجلد SAF مخصص للتحميل"
                                    }
                                } catch(e: Exception) {
                                    "مجلد نظام مخصص"
                                }
                            } else {
                                defaultDownloadPath
                            }
                            Text(
                                text = readablePath,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "تعديل المسار",
                                tint = Color(accentColorHex),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // حالة وتحكم صلاحيات الوصول للتخزين
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صلاحية الوصول الكامل للذاكرة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (hasStoragePermission) 
                                    "الحالة: تم منح الصلاحية بنجاح ✅" 
                                else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) 
                                    "مطلوبة للتنزيل المباشر بالقرص على أندرويد 11+ ⚠️" 
                                else 
                                    "مطلوبة لكتابة وحفظ الملفات بالذاكرة ⚠️", 
                                fontSize = 10.sp, 
                                color = if (hasStoragePermission) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.R || android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.S || android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.S_V2) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        storagePermissionLauncher.launch(com.example.data.download.PermissionHandler.getRequiredPermissions())
                                    }
                                } else {
                                    storagePermissionLauncher.launch(com.example.data.download.PermissionHandler.getRequiredPermissions())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasStoragePermission) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444),
                                contentColor = if (hasStoragePermission) Color(0xFF10B981) else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(35.dp)
                        ) {
                            Text(if (hasStoragePermission) "مفعّلة" else "منح الإذن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // التنظيم التلقائي للملفات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل التنظيم التلقائي للمجلدات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("حفظ الفيديوهات في /Videos والصوتيات في /Audio والمستندات تلقائياً", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoOrganizeFiles,
                            onCheckedChange = { value ->
                                scope.launch { settingsManager.setAutoOrganizeFiles(value) }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(accentColorHex))
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // المساحة المستخدمة ومسح الملفات المؤقتة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("المساحة المستخدمة بالتطبيق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatSize(appStorageBytes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(accentColorHex)
                            )
                        }
                        Button(
                            onClick = {
                                val successfullyCleared = settingsManager.clearCacheFiles()
                                if (successfullyCleared) {
                                    appStorageBytes = settingsManager.getAppUsedStorageBytes()
                                    Toast.makeText(context, "تم مسح الذاكرة الفيدوية والصوتية المؤقتة بنجاح 🧹", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "فشل في مسح التخزين المؤقت", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("مسح الملفات المؤقتة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ج) قسم "الإشعارات"
            SettingsSectionHeader(title = "تنبيهات النظام والإشعارات", icon = Icons.Default.Notifications)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NotificationToggleItem(
                        title = "تنبيهات التحميل النشطة لشريط المهام",
                        description = "لمراقبة شريط التقدم والسرعة اللحظية أثناء جريان التحميل",
                        checked = notifActive,
                        enabledColor = Color(accentColorHex),
                        onCheckedChange = { scope.launch { settingsManager.setNotifActive(it) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))

                    NotificationToggleItem(
                        title = "تنبيهات اكتمال التنزيل",
                        description = "تنبيهك فوراً عند اكتمال تنزيل أي ملف وصوت مع زر قنوات الفتح المباشر",
                        checked = notifCompleted,
                        enabledColor = Color(accentColorHex),
                        onCheckedChange = { scope.launch { settingsManager.setNotifCompleted(it) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))

                    NotificationToggleItem(
                        title = "تنبيهات فشل التنزيل",
                        description = "إعلامك فور وقوع أخطاء متعلقة بكفاءة الشبكة أو الخادم للمحاولة",
                        checked = notifFailed,
                        enabledColor = Color(accentColorHex),
                        onCheckedChange = { scope.launch { settingsManager.setNotifFailed(it) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))

                    NotificationToggleItem(
                        title = "تمديدات تشغيل الصوت",
                        description = "تفعيل الصوت للإشعارات المكتملة أو الفاشلة من نظام هاتفك",
                        checked = notifSound,
                        enabledColor = Color(accentColorHex),
                        onCheckedChange = { scope.launch { settingsManager.setNotifSound(it) } }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))

                    NotificationToggleItem(
                        title = "تمديدات الاهتزاز والرجّ",
                        description = "تفعيل رنين الاهتزاز فور اكتمال معالجة وبناء الفيديو بنجاح",
                        checked = notifVibrate,
                        enabledColor = Color(accentColorHex),
                        onCheckedChange = { scope.launch { settingsManager.setNotifVibrate(it) } }
                    )
                }
            }

            // د) قسم "المظهر"
            SettingsSectionHeader(title = "المظهر والتخصيص الرائع", icon = Icons.Default.Palette)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // اختيار نوع القالب العام للتطبيق Theme
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("قالب المظهر العام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("System" to "النظام", "Light" to "فاتح", "Dark" to "داكن").forEach { (mode, label) ->
                                val isSelected = themeMode == mode
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clickable {
                                            scope.launch { settingsManager.setThemeMode(mode) }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(accentColorHex) else MaterialTheme.colorScheme.surface,
                                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = if (!isSelected) borderStroke() else null
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // اختيار اللون الرئيسي Accent (Color Picker الدائري)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لون التمييز والدرجات (Accent Color)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            availableAccentColors.forEach { color ->
                                val isSelected = accentColorHex == color.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch { settingsManager.setAccentColor(color.toArgb()) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "محدد",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // هـ) قسم "حول التطبيق"
            SettingsSectionHeader(title = "حول التطبيق والمعمارية", icon = Icons.Default.Info)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = Color(accentColorHex),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Pro Downloader v1.2",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "تطبيق فائق السرعة يعزز من كفاءة بروتوكولات الشبكة المتعددة لتجزئة وتنزيل الملفات وقنص الفيديوهات والبث التلفزيوني عبر الخيوط المتزامنة بنظام أندرويد الحديث ومبني بلغة Kotlin و Jetpack Compose بالكامل وفق قواعد معمارية MVVM الصلبة مع التخزين الآمن DataStore.",
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { showPrivacyDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(accentColorHex).copy(alpha = 0.11f),
                            contentColor = Color(accentColorHex)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("سياسة الخصوصية والاستخدام الآمن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialog لتغيير مسار الحفظ الافتراضي
    if (showPathEditDialog) {
        AlertDialog(
            onDismissRequest = { showPathEditDialog = false },
            title = { Text("تعديل مسار التخزين والوجهة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل المسار الكامل على القرص. تأكد من توفر صلاحيات الكتابة.", fontSize = 11.sp)
                    OutlinedTextField(
                        value = inputPathText,
                        onValueChange = { inputPathText = it },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = File(inputPathText)
                        if (inputPathText.isNotEmpty()) {
                            scope.launch { settingsManager.setDefaultDownloadPath(inputPathText) }
                            showPathEditDialog = false
                            Toast.makeText(context, "تم حفظ وتحديث مسار الوجهة بنجاح", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "مسار خاطئ أو فارغ", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("تحديث المسار", color = Color(accentColorHex), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPathEditDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Dialog لسياسة الخصوصية
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("سياسة الخصوصية والاستخدام") },
            text = {
                Text(
                    text = "يتعهد تطبيق Pro Downloader بحماية بياناتك وخصوصيتك بالكامل. جميع عمليات التنزيل، وتجزئة الملفات عبر الخيوط المتزامنة، وإعدادات تطبيقك المشفرة بموجب DataStore، تتم محلياً ومستقلاً تماماً على جهازك ولا نقوم بأي محاولة لجمع أي معلومات أو استخراج روابط فك تشفير الفيديوهات خارج نطاق الاستخدام الشخصي المباشر للتطبيق.\n\nجميع الصلاحيات المطلوبة تهدف بحت لمقاومة الحجب وجلب التنزيلات الكبيرة.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("أفهم وأوافق", color = Color(accentColorHex), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF8B5CF6).copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NotificationToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    enabledColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = enabledColor)
        )
    }
}

@Composable
private fun borderStroke() = androidx.compose.foundation.BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 بايت"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f جيجابايت", gb)
        mb >= 1.0 -> String.format("%.2f ميجابايت", mb)
        kb >= 1.0 -> String.format("%.1f كيلوبايت", kb)
        else -> "$bytes بايت"
    }
}
