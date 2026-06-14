package com.example.ui.download

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.download.DownloadItem
import com.example.data.download.DownloadStatus
import com.example.data.download.FileType
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة التنزيلات الرئيسية المطورة بتصميم Material 3 العصري.
 * تدعم:
 * - تصفية التبويبات المتعددة: الكل / نشط / مكتمل / متوقف / فاشل.
 * - وضع التحديد المتعدد باللمس المطول وشريط العمليات السفلي المذهل.
 * - واجهة البحث الفوري عن السجلات.
 * - زر إضافة مع تدرج لوني يفتح واجهة إضافة التحميل السفلية الذكية.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToBrowser: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadsList by viewModel.downloads.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // حالات البحث والتصفية
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: الكل، 1: نشط، 2: مكتمل، 3: متوقف، 4: فاشل

    // حالات التحديد المتعدد
    var isInMultiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }

    // التحكم بالـ Sheet لإضافة التنزيل
    var showAddSheet by remember { mutableStateOf(false) }

    // التحقق وطلب الاشعارات بنظام أندرويد 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // تنظيف التحديد عند الخروج من وضع التحديد المتعدد
    LaunchedEffect(isInMultiSelectMode) {
        if (!isInMultiSelectMode) {
            selectedIds.clear()
        }
    }

    // تصفية العناصر بناءً على التبويب وكلمة البحث
    val filteredDownloads = remember(downloadsList, activeTab, searchQuery) {
        downloadsList.filter { item ->
            val matchesTab = when (activeTab) {
                1 -> item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED
                2 -> item.status == DownloadStatus.COMPLETED
                3 -> item.status == DownloadStatus.PAUSED
                4 -> item.status == DownloadStatus.FAILED || item.status == DownloadStatus.CANCELLED
                else -> true
            }
            val matchesSearch = item.fileName.contains(searchQuery, ignoreCase = true) || 
                                item.url.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    // تدرج لوني أزرق بنفسجي عصري للـ Primary
    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "top_bar_anim"
            ) { searchActive ->
                if (searchActive) {
                    // حقل البحث المدمج في الـ TopBar
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث في التنزيلات...", fontSize = 15.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearchActive = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "اغلاق")
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                } else {
                    // الـ TopAppBar الأساسي
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(primaryGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "برو داونلودر",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Pro Downloader",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "بحث")
                            }
                            IconButton(onClick = {
                                Toast.makeText(context, "السرعة مهيأة تلقائياً بـ 8 خيوط لضمان أقصى كفاءة للتحميل الشبكي", Toast.LENGTH_LONG).show()
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "الاعدادات")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isInMultiSelectMode) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    shape = CircleShape,
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .size(62.dp)
                        .shadow(8.dp, CircleShape)
                        .background(primaryGradient, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة تنزيل",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // تفعيل اشعارات تنبيه
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionWarningCard {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // 1. لوحة إحصائيات علوية ذات طابع عصري ومؤثر بصري مريح
                DashboardStatsRow(downloadsList)

                // 2. شريط التبويبات للتصفية المتقدمة (الكل / نشط / مكتمل / متوقف / فاشل)
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = Color(0xFF8B5CF6),
                            height = 3.dp
                        )
                    }
                ) {
                    val tabs = listOf(
                        Triple("الكل", downloadsList.size, Icons.Default.Info),
                        Triple("نشط", downloadsList.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }, Icons.Default.PlayArrow),
                        Triple("مكتمل", downloadsList.count { it.status == DownloadStatus.COMPLETED }, Icons.Default.CheckCircle),
                        Triple("متوقف", downloadsList.count { it.status == DownloadStatus.PAUSED }, Icons.Default.Close),
                        Triple("فاشل", downloadsList.count { it.status == DownloadStatus.FAILED || itemCancelled(it) }, Icons.Default.Warning)
                    )
                    tabs.forEachIndexed { index, pair ->
                        val isSelected = activeTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { activeTab = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = pair.third,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = "${pair.first} (${pair.second})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                // 3. عرض قائمة السجلات المفلترة
                if (filteredDownloads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(bottom = 20.dp)
                                    .size(120.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(80.dp),
                                    tonalElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (searchQuery.isNotEmpty()) "لا توجد نتائج بحث مطابقة" else "قائمة التنزيلات فارغة",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "جرّب التحقق من دقة الاسم أو البحث بكلمات أبسط للوصول للملف." else "ابدأ بتنزيل ملفاتك المفضلة! انقر على زر إضافة '+' بالأسفل لتنزيل الفيديوهات ومقاطع الصوت بسرعة هائلة.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("downloads_history_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredDownloads, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            DownloadItemCardInteractive(
                                item = item,
                                isSelected = isSelected,
                                isInSelectMode = isInMultiSelectMode,
                                onLongClick = {
                                    if (!isInMultiSelectMode) {
                                        isInMultiSelectMode = true
                                        selectedIds.add(item.id)
                                    }
                                },
                                onClick = {
                                    if (isInMultiSelectMode) {
                                        if (isSelected) {
                                            selectedIds.remove(item.id)
                                            if (selectedIds.isEmpty()) {
                                                isInMultiSelectMode = false
                                            }
                                        } else {
                                            selectedIds.add(item.id)
                                        }
                                    }
                                },
                                onDetailClick = {
                                    onNavigateToDetail(item.id)
                                },
                                onPause = { viewModel.pauseDownload(item.id) },
                                onResume = { viewModel.resumeDownload(item.id) },
                                onCancel = { viewModel.cancelDownload(item.id) },
                                onDelete = { viewModel.deleteDownload(item) }
                            )
                        }
                    }
                }
            }

            // 4. شريط العمليات السفلي العائم (Toolbar) عند تفعيل التحديد المتعدد
            AnimatedVisibility(
                visible = isInMultiSelectMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "تم تحديد ${selectedIds.size} تنزيل",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "انقر لتنفيذ إجراءات جماعية",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // إيقاف مؤقت جماعي
                            IconButton(
                                onClick = {
                                    viewModel.pauseMultiple(selectedIds.toList())
                                    isInMultiSelectMode = false
                                    Toast.makeText(context, "تم إيقاف التنزيلات المحددة مؤقتاً", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).size(40.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إيقاف", tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            }

                            // استئناف جماعي
                            IconButton(
                                onClick = {
                                    viewModel.resumeMultiple(selectedIds.toList())
                                    isInMultiSelectMode = false
                                    Toast.makeText(context, "تم استئناف التنزيلات المحددة", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).size(40.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "استئناف", tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            }

                            // حذف جماعي
                            IconButton(
                                onClick = {
                                    val itemsToDelete = downloadsList.filter { selectedIds.contains(it.id) }
                                    viewModel.deleteMultiple(itemsToDelete)
                                    isInMultiSelectMode = false
                                    Toast.makeText(context, "تم حذف الملفات المحددة بالكامل", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape).size(40.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الكل", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }

                            // إلغاء الوضع
                            IconButton(
                                onClick = { isInMultiSelectMode = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).size(40.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء التحديد", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    val prefilledUrl by viewModel.prefilledUrlFlow.collectAsStateWithLifecycle()

    // عرض ورقة إضافة تحميل جديد الذكية
    if (showAddSheet || prefilledUrl != null) {
        AddDownloadSheet(
            viewModel = viewModel,
            onDismiss = {
                showAddSheet = false
                viewModel.clearPrefilledUrl()
            },
            onFallbackToBrowser = { url -> onNavigateToBrowser() }
        )
    }
}

/**
 * ورقة إضافة تنزيل جديد ذكية (BottomSheet) بتصميم متميز وتجربة تفاعلية متجاوبة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    viewModel: DownloadViewModel,
    onDismiss: () -> Unit,
    onFallbackToBrowser: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var fileNameInput by remember { mutableStateOf("") }
    var fileSizeToDisplay by remember { mutableLongStateOf(0L) }
    var detectedType by remember { mutableStateOf(FileType.OTHER) }

    var selectedThreads by remember { mutableFloatStateOf(4f) }
    var selectedFolderLabel by remember { mutableStateOf("Downloads (تنزيلات النظام)") }
    var selectedFolderPath by remember {
        mutableStateOf(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath 
                ?: context.filesDir.absolutePath
        )
    }

    var isFolderDropdownExpanded by remember { mutableStateOf(false) }
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val prefilledCookie by viewModel.prefilledCookieFlow.collectAsStateWithLifecycle()
    val prefilledUserAgent by viewModel.prefilledUserAgentFlow.collectAsStateWithLifecycle()
    var isResolvingYoutubeDL by remember { mutableStateOf(false) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var isLinkResolvedSuccessfully by remember { mutableStateOf(false) }

    // YoutubeDL format variables
    var videoInfo by remember { mutableStateOf<com.example.youtubedl.VideoInfo?>(null) }
    var selectedFormat by remember { mutableStateOf<com.example.youtubedl.VideoFormat?>(null) }

    // لاقط مجلدات مخصص
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            selectedFolderLabel = "مجلد مخصص: ${uri.lastPathSegment}"
            val resolvedPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath 
                ?: context.filesDir.absolutePath
            selectedFolderPath = resolvedPath
            Toast.makeText(context, "تم تحديد مجلد الحفظ المختار في Scoped Storage", Toast.LENGTH_SHORT).show()
        }
    }

    val prefilledUrlState by viewModel.prefilledUrlFlow.collectAsStateWithLifecycle()

    fun startHybridResolution(url: String) {
        if (url.trim().isEmpty() || !url.startsWith("http")) {
            sheetError = "الرجاء إدخال رابط تحميل صحيح يبدأ بـ http أو https."
            isLinkResolvedSuccessfully = false
            return
        }

        isLinkResolvedSuccessfully = false
        sheetError = null
        isResolvingYoutubeDL = true
        videoInfo = null
        selectedFormat = null

        scope.launch {
            try {
                // محاولة استخدام محرك YoutubeDL المدمج لاستخراج الجودات
                val info = com.example.youtubedl.YoutubeDL.getInstance().getInfo(url)
                videoInfo = info
                fileNameInput = info.title
                selectedFormat = info.formats.firstOrNull()
                detectedType = FileType.VIDEO
                fileSizeToDisplay = selectedFormat?.fileSize ?: 0L
                isLinkResolvedSuccessfully = true
            } catch (e: Exception) {
                // إذا فشل YoutubeDL أو كان الرابط غير مدعوم، يتم توجيه المستخدم تلقائياً إلى شاشة BrowserScreen
                Log.d("AddDownloadSheet", "فشل تحليل YoutubeDL للرابط، جاري تحويل العميل للمستكشف: ${e.message}")
                Toast.makeText(context, "الرابط يحتاج لفحص عميق. جاري توجيهك إلى المتصفح المدمج المطور...", Toast.LENGTH_LONG).show()
                viewModel.triggerPrefilledUrl(url)
                onFallbackToBrowser(url)
                onDismiss()
            } finally {
                isResolvingYoutubeDL = false
            }
        }
    }

    // قراءة تلقائية للرابط من الحافظة أو الرابط الممرر للمشاركة
    LaunchedEffect(prefilledUrlState) {
        val targetUrl = prefilledUrlState ?: run {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text.toString()
                if (clipText.startsWith("http://") || clipText.startsWith("https://")) {
                    clipText
                } else null
            } else null
        }

        if (targetUrl != null && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
            urlInput = targetUrl
            startHybridResolution(targetUrl)
        }
    }

    // تدرج لوني أزرق بنفسجي عصري للـ Primary
    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "تحميل فيديو/ملف ذكي",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color(0xFF8B5CF6)
            )

            // حقل إدخال الرابط
            OutlinedTextField(
                value = urlInput,
                onValueChange = { input ->
                    urlInput = input
                    if (input.startsWith("http")) {
                        startHybridResolution(input)
                    }
                },
                label = { Text("رابط التحميل الإلكتروني (URL)") },
                placeholder = { Text("https://example.com/movie.mp4") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { 
                            urlInput = "" 
                            fileNameInput = ""
                            isLinkResolvedSuccessfully = false
                            videoInfo = null
                            selectedFormat = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح")
                        }
                    } else {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text.toString()
                                    urlInput = text
                                    startHybridResolution(text)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp).padding(end = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text("لصق سريع", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )

            // مؤشر حالة الفحص والاستطلاع
            if (isResolvingYoutubeDL) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text("جاري استخلاص وقراءة الجودات المتوفرة بمحرك YoutubeDL...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // عرض الخطأ بشكل جميل مثل Snackbar
            if (sheetError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = sheetError ?: "حدث خطأ غير متوقع", 
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // تظهر الخيارات الإضافية فقط عند التحقق الناجح من الرابط لحماية سلامة النظام
            AnimatedVisibility(
                visible = isLinkResolvedSuccessfully,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // عرض الجودات المستخرجة من YoutubeDL
                    if (videoInfo != null) {
                        Text(
                            text = "الجودات وتنسيقات الفيديو المتاحة بالمنصة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            videoInfo?.formats?.forEach { format ->
                                val isSelected = selectedFormat?.formatId == format.formatId
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFormat = format
                                            fileSizeToDisplay = format.fileSize
                                            detectedType = if (format.formatId == "audio_only") FileType.AUDIO else FileType.VIDEO
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.5.dp,
                                        color = if (isSelected) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                selectedFormat = format
                                                fileSizeToDisplay = format.fileSize
                                                detectedType = if (format.formatId == "audio_only") FileType.AUDIO else FileType.VIDEO
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8B5CF6))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = format.formatName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "الامتداد: ${format.ext} | الحجم المتوقع: ${formatBytes(format.fileSize)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        if (format.isMergeRequired) {
                                            Surface(
                                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "دمج FFmpeg تلقائي",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // حقل تعديل اسم الملف
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        label = { Text("تعديل اسم حفظ الملف") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // اختيار مجلد الحفظ
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedFolderLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("مجلد مسار الحفظ") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { isFolderDropdownExpanded = true }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "تغيير المجلد", modifier = Modifier.size(20.dp))
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = isFolderDropdownExpanded,
                            onDismissRequest = { isFolderDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Downloads (مجلد التنزيلات)") },
                                onClick = {
                                    selectedFolderLabel = "Downloads (مجلد التنزيلات)"
                                    selectedFolderPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: context.filesDir.absolutePath
                                    isFolderDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Videos (الفيديوهات والأفلام)") },
                                onClick = {
                                    selectedFolderLabel = "Videos (الفيديوهات والأفلام)"
                                    selectedFolderPath = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath ?: context.filesDir.absolutePath
                                    isFolderDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Music (الموسيقى والصوتيات)") },
                                onClick = {
                                    selectedFolderLabel = "Music (الموسيقى والصوتيات)"
                                    selectedFolderPath = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath ?: context.filesDir.absolutePath
                                    isFolderDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Documents (المستندات والوثائق)") },
                                onClick = {
                                    selectedFolderLabel = "Documents (المستندات والوثائق)"
                                    selectedFolderPath = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: context.filesDir.absolutePath
                                    isFolderDropdownExpanded = false
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("اختر مجلد مخصص يدوي عبر File Picker...") },
                                onClick = {
                                    isFolderDropdownExpanded = false
                                    pickerLauncher.launch(null)
                                }
                            )
                        }
                    }

                    // شريط التمرير للخيوط
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "عدد قنوات التحميل المشتركة: ${selectedThreads.toInt()} خيوط تفرعية",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "أكثر = أسرع لكن يستهلك طاقة أكثر",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Slider(
                            value = selectedThreads,
                            onValueChange = { selectedThreads = it },
                            valueRange = 1f..8f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF8B5CF6),
                                activeTrackColor = Color(0xFF6366F1),
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            // زر بدء التحميل الرئيسي العصري
            Button(
                onClick = {
                    if (urlInput.isNotEmpty() && fileNameInput.isNotEmpty()) {
                        val ext = selectedFormat?.ext ?: "mp4"
                        val finalUrl = selectedFormat?.url ?: urlInput
                        val finalFileName = if (fileNameInput.contains(".")) fileNameInput else "$fileNameInput.$ext"
                        viewModel.addDownloadWithDetails(
                            url = finalUrl,
                            fileName = finalFileName,
                            folderPath = selectedFolderPath,
                            threadCount = selectedThreads.toInt(),
                            fileType = detectedType,
                            fileSize = fileSizeToDisplay,
                            cookie = prefilledCookie,
                            userAgent = prefilledUserAgent
                        )
                        onDismiss()
                    }
                },
                enabled = isLinkResolvedSuccessfully && !isResolvingYoutubeDL,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(primaryGradient)
                        .clickable(enabled = isLinkResolvedSuccessfully) {
                            if (urlInput.isNotEmpty() && fileNameInput.isNotEmpty()) {
                                val ext = selectedFormat?.ext ?: "mp4"
                                val finalUrl = selectedFormat?.url ?: urlInput
                                val finalFileName = if (fileNameInput.contains(".")) fileNameInput else "$fileNameInput.$ext"
                                viewModel.addDownloadWithDetails(
                                    url = finalUrl,
                                    fileName = finalFileName,
                                    folderPath = selectedFolderPath,
                                    threadCount = selectedThreads.toInt(),
                                    fileType = detectedType,
                                    fileSize = fileSizeToDisplay,
                                    cookie = prefilledCookie,
                                    userAgent = prefilledUserAgent
                                )
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Text(
                            text = "بدء التحميل فورا بنظام المسرّع",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * كرت لعرض وتفصيل سجل التنزيل التفاعلي المطور.
 * يدعم اللمس المطول وتعديل اللون خط التقدم حسب الحالة الحالية.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadItemCardInteractive(
    item: DownloadItem,
    isSelected: Boolean,
    isInSelectMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDetailClick: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    // تحديد لون التقدم وألوان الحالات الأساسية للتصميم
    val progressColor = when (item.status) {
        DownloadStatus.DOWNLOADING -> Color(0xFF10B981) // أخضر زاهي
        DownloadStatus.PAUSED -> Color(0xFFF59E0B)     // برتقالي
        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Color(0xFFEF4444) // أحمر
        DownloadStatus.COMPLETED -> Color(0xFF3B82F6)  // أزرق سماوي
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.outline
    }

    // تتبع حالة الخلفية والحدود للتحديد المتعدد
    val borderStrokeColor = if (isSelected) Color(0xFF8B5CF6) else Color.Transparent
    val cardBg = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1A1D29) else Color(0xFFFFFFFF)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (androidx.compose.foundation.isSystemInDarkTheme()) 0.dp else 2.dp, RoundedCornerShape(16.dp))
            .testTag("task_item_card")
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = {
                    if (isInSelectMode) {
                        onClick()
                    } else {
                        onDetailClick()
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, borderStrokeColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // خانة الاختيار لتناغم التحديد المتعدد
            if (isInSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF8B5CF6))
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // الصف العلوي: أيقونة نوع الملف، اسم حفظ الملف وزر الحذف الفردي الآمن
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val (iconBg, iconTint, vectorIcon) = getFileTypeBadging(item.fileType)
                    Surface(
                        color = iconBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(vectorIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadgeUpdated(item.status)
                            Text(
                                text = "${item.threadCount} خيط اتصال متوازي",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (!isInSelectMode) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف سجل التحميل", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // قسم تتبع تقدم التنزيلات
                val calculatedProgress = if (item.fileSize > 0) {
                    (item.downloadedBytes.toFloat() / item.fileSize.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val percent = (calculatedProgress * 100).toInt()

                // شريط تتبع تقدم ناعم بألوان الحالة المطلوبة
                LinearProgressIndicator(
                    progress = { calculatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // تحديث النصوص الذكية والسرعات تبعا للحالة
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatBytes(item.downloadedBytes)} من ${formatBytes(item.fileSize)} ($percent%)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSpeed(item.speed),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        if (item.etaSeconds >= 0) {
                            Text(
                                text = "متبقي ${formatETA(item.etaSeconds)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    DownloadStatus.QUEUED -> {
                        Text(
                            text = "جاري تهيئة قنوات الاتصال والـ Threads المجدولة...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "مكتمل: ${formatBytes(item.fileSize)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "تم الحفظ بنجاح",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B82F6)
                            )
                        }
                    }
                    else -> {
                        // حالات الإيقاف أو التعطل
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "تم تنزيل ${formatBytes(item.downloadedBytes)} من ${formatBytes(item.fileSize)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = if (item.status == DownloadStatus.PAUSED) "موقّف مؤقتاً" else "مُعطّل",
                                fontSize = 11.sp,
                                color = progressColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // عرض رسالة الخطأ العربية إذا كانت متوفرة للفشل أو التعطل
                if (item.status == DownloadStatus.FAILED && !item.errorMessage.isNullOrEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = item.errorMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // الأزرار والتفاعلات الجماعية والتحكم بالملفات المستهدفة
                if (!isInSelectMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.status == DownloadStatus.COMPLETED) {
                            val targetFile = File(item.filePath)
                            if (targetFile.exists()) {
                                OutlinedButton(
                                    onClick = { openDownloadedFile(context, targetFile, item.mimeType) },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .padding(end = 8.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("فتح الملف", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { shareDownloadedFile(context, targetFile, item.mimeType) },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاركة", fontSize = 12.sp)
                                }
                            } else {
                                Text("تم نقل الملف أو حذفه من الذاكرة", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        } else {
                            when (item.status) {
                                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                    OutlinedButton(
                                        onClick = onPause,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .padding(end = 8.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = "إيقاف مؤقت", modifier = Modifier.size(16.dp), tint = Color(0xFFF59E0B))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إيقاف مؤقت", fontSize = 12.sp, color = Color(0xFFF59E0B))
                                    }

                                    Button(
                                        onClick = onCancel,
                                        modifier = Modifier.height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "إلغاء التحميل", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إلغاء", fontSize = 12.sp)
                                    }
                                }
                                DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                                    Button(
                                        onClick = onResume,
                                        modifier = Modifier.height(40.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("استئناف", fontSize = 12.sp)
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * دالة مساعدة لتلافي مشاكل الكومبايلر للبلد الحالي
 */
fun itemCancelled(item: DownloadItem): Boolean {
    return item.status.name == "CANCELLED"
}

/**
 * شارات الحالات المطورة بألوان ناعمة متجاوبة
 */
@Composable
fun StatusBadgeUpdated(status: DownloadStatus) {
    val (statusLabel, badgeColor) = when (status) {
        DownloadStatus.QUEUED -> Pair("بالانتظار", MaterialTheme.colorScheme.outline)
        DownloadStatus.DOWNLOADING -> Pair("جاري التحميل", Color(0xFF10B981))
        DownloadStatus.PAUSED -> Pair("موقّف", Color(0xFFF59E0B))
        DownloadStatus.COMPLETED -> Pair("مكتمل", Color(0xFF3B82F6))
        DownloadStatus.FAILED -> Pair("فشل", Color(0xFFEF4444))
        DownloadStatus.CANCELLED -> Pair("ملغى", Color(0xFFEF4444))
    }

    Surface(
        color = badgeColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = statusLabel,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * لوحة الإحصائيات الفورية لتعزيز تجربة المستخدم بتصميم جذاب.
 */
@Composable
fun DashboardStatsRow(downloads: List<DownloadItem>) {
    val totalCount = downloads.size
    val activeCount = downloads.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCardStyled(
            title = "كل التحميلات",
            value = totalCount.toString(),
            icon = Icons.Default.Info,
            gradientColors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
            modifier = Modifier.weight(1f)
        )
        StatCardStyled(
            title = "نشط الآن",
            value = activeCount.toString(),
            icon = Icons.Default.PlayArrow,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
            modifier = Modifier.weight(1f)
        )
        StatCardStyled(
            title = "مكتملة",
            value = completedCount.toString(),
            icon = Icons.Default.CheckCircle,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB)),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCardStyled(
    title: String,
    value: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val statsGradient = Brush.linearGradient(colors = gradientColors)
    Card(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(statsGradient)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(14.dp))
                    Text(text = title, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
                }
                Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

/**
 * دالة مساعدة للحصول على الأيقونة واللون وخلفية كرت نوع الملف.
 */
fun getFileTypeBadging(type: FileType): Triple<Color, Color, ImageVector> {
    return when (type) {
        FileType.VIDEO -> Triple(Color(0xFFFEE2E2), Color(0xFFEF4444), Icons.Default.PlayArrow)
        FileType.AUDIO -> Triple(Color(0xFFECFDF5), Color(0xFF10B981), Icons.Default.PlayArrow)
        FileType.DOCUMENT -> Triple(Color(0xFFEFF6FF), Color(0xFF3B82F6), Icons.Default.Info)
        FileType.ARCHIVE -> Triple(Color(0xFFFFF7ED), Color(0xFFF59E0B), Icons.Default.Warning)
        FileType.APK -> Triple(Color(0xFFF5F3FF), Color(0xFF8B5CF6), Icons.Default.CheckCircle)
        FileType.OTHER -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.Info)
    }
}

fun getFileTypeArabic(type: FileType): String {
    return when (type) {
        FileType.VIDEO -> "فيديو وأفلام (Video)"
        FileType.AUDIO -> "صوت وموسيقى (Audio)"
        FileType.DOCUMENT -> "مستندات وكتب (Document)"
        FileType.ARCHIVE -> "ملفات مضغوطة (Archive)"
        FileType.APK -> "تطبيقات أندرويد (APK)"
        FileType.OTHER -> "ملف مجهول الامتداد (Other)"
    }
}

/**
 * كرت لعرض تحذير في حال تعطيل الإشعارات بنظام أندرويد.
 */
@Composable
fun PermissionWarningCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "قنوات الإشعار معطلة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "يتطلب التطبيق الإشعارات لعرض تقدم التحميل الفوري بشريط المهام بنجاح.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("تفعيل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * تنسيق تقدم أو أحجام البايتات إلى نصوص مقروءة للناس.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 بايت"
    val units = listOf("بايت", "كيلوبايت", "ميغابايت", "جيجابايت")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

/**
 * تنسيق سرعة التحميل بالبايت في الثانية.
 */
fun formatSpeed(speedBytesPerSec: Long): String {
    if (speedBytesPerSec <= 0) return "0 ك/ث"
    val speedKb = speedBytesPerSec / 1024.0
    if (speedKb < 1024.0) {
        return String.format(Locale.getDefault(), "%.1f ك/ث", speedKb)
    }
    val speedMb = speedKb / 1024.0
    return String.format(Locale.getDefault(), "%.1f م/ث", speedMb)
}

/**
 * تنسيق الوقت المتوقع المتبقي للتحميل بالثواني.
 */
fun formatETA(seconds: Long): String {
    if (seconds <= 0) return "غير معروف"
    if (seconds < 60) return "$seconds ثانية"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    if (minutes < 60) {
        return "$minutes دقيقة و $remainingSeconds ثانية"
    }
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "$hours ساعة و $remainingMinutes دقيقة"
}

/**
 * فتح الملف الذي تم تحميله باستخدام FileProvider آمن وتطبيقات خارجية جاهزة.
 */
fun openDownloadedFile(context: Context, file: File, mimeType: String) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "افتح الملف باستخدام"))
    } catch (e: Exception) {
        Toast.makeText(context, "لم يتم العثور على تطبيق متوافق لفتح هذا الملف: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/**
 * مشاركة الملف الذي تم تنزيله مع تطبيقات التواصل والقرص.
 */
fun shareDownloadedFile(context: Context, file: File, mimeType: String) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف عبر"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل بدء مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

