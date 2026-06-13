package com.example.ui.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.download.DownloadItem
import com.example.data.download.DownloadStatus
import com.example.data.download.FileType
import com.example.ui.download.DownloadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * شاشة الملفات (FilesScreen):
 * تعرض الملفات المُحمّلة بالكامل مصنفة في تبويبات مع فلاتر البحث والترتيب والوصول الآمن لتطبيقات الهاتف.
 * شرح هام لاختبار Scoped Storage في نظام Android 11+:
 * - في Android 11 فما فوق، لا يمكن للتطبيقات قراءة جميع مسارات التخزين عشوائياً بدون الحصول على تصريح MANAGE_EXTERNAL_STORAGE.
 * - بالنسبة للتنزيلات داخل التطبيق (ExternalAppFiles)، يتم حفظها وضمان فتحها بدون مشاكل باستخدام FileProvider.
 * - للاختبار: يُفضل استخدام أدوات المحاكي أو جهاز حقيقي، وإثبات أن FileProvider يعبر عبر الحماية من خلال منح أذونات القراءة المؤقتة FLAG_GRANT_READ_URI_PERMISSION.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    // تصفية الملفات المكتملة فقط
    val completedFiles = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED }
    }

    // فلتر البحث والترتيب والتبويبات
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("newest") } // newest, largest, name
    var activeTab by remember { mutableIntStateOf(0) } // 0: فيديو، 1: صوت، 2: مستندات، 3: أرشيف، 4: أخرى
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val tabs = listOf(
        "فيديوهات" to Icons.Default.Movie,
        "صوتيات" to Icons.Default.MusicNote,
        "مستندات" to Icons.Default.Description,
        "أرشيف" to Icons.Default.Archive,
        "أخرى" to Icons.Default.Extension
    )

    // تصفية الملفات حسب نوع التبويب النشط
    val tabFilteredList = remember(completedFiles, activeTab) {
        completedFiles.filter { item ->
            when (activeTab) {
                0 -> item.fileType == FileType.VIDEO
                1 -> item.fileType == FileType.AUDIO
                2 -> item.fileType == FileType.DOCUMENT
                3 -> item.fileType == FileType.ARCHIVE
                4 -> item.fileType == FileType.APK || item.fileType == FileType.OTHER
                else -> true
            }
        }
    }

    // فلترة بالبحث والترتيب
    val finalFilesList = remember(tabFilteredList, searchQuery, sortBy) {
        var list = tabFilteredList.filter {
            it.fileName.contains(searchQuery, ignoreCase = true)
        }
        list = when (sortBy) {
            "largest" -> list.sortedByDescending { it.fileSize }
            "name" -> list.sortedBy { it.fileName.lowercase() }
            else -> list.sortedByDescending { it.createdAt } // newest
        }
        list
    }

    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "مدير الملفات المحملة",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    // زر فرز القائمة بأكملها
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "ترتيب")
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("الأحدث أولاً") },
                            onClick = {
                                sortBy = "newest"
                                sortMenuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("الحجم الأكبر") },
                            onClick = {
                                sortBy = "largest"
                                sortMenuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.TrendingDown, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("الاسم أبجديًا") },
                            onClick = {
                                sortBy = "name"
                                sortMenuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // شريط إدخال نص البحث
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في ملفاتك المعالجة...", fontSize = 13.sp) },
                prefix = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    focusedIndicatorColor = Color(0xFF8B5CF6)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("files_search_input")
            )

            // شريط تبويبات التصنيف
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = Color(0xFF8B5CF6)
                    )
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        selectedContentColor = Color(0xFF8B5CF6),
                        unselectedContentColor = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // عرض محتوى الشبكة
            if (finalFilesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك" else "لم يتم تحميل أي ملف في هذا القسم بعد",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تنزيل الفيديوهات ومقتطفات الويب سيعرضها هنا تلقائياً.",
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(finalFilesList, key = { it.id }) { item ->
                        FileCard(
                            item = item,
                            onClick = { openDownloadedFile(context, item) },
                            onShare = { shareDownloadedFile(context, item) },
                            onDelete = { viewModel.deleteDownload(item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * الكارت الفردي لعرض وتخريط ملف الإنزال
 */
@Composable
fun FileCard(
    item: DownloadItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var deleteDialogPresented by remember { mutableStateOf(false) }

    if (deleteDialogPresented) {
        AlertDialog(
            onDismissRequest = { deleteDialogPresented = false },
            title = { Text("حذف الملف بشكل نهائي") },
            text = { Text("هل أنت متأكد من رغبتك في حذف '${item.fileName}' من الذاكرة والقرص؟ هذه الخطوة غير قابلة للتراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    deleteDialogPresented = false
                }) {
                    Text("نعم، احذف", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogPresented = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            // جزء المعاينة (فيديو أو أيقونة مخصصة)
            if (item.fileType == FileType.VIDEO) {
                VideoThumbnailExtractor(
                    filePath = item.filePath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color.Black.copy(alpha = 0.05f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val (icon, color) = getIconAndColorForFileType(item.fileType)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            // تفاصيل وبيانات الملف
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatSize(item.fileSize),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )

                    val dateStr = remember(item.createdAt) {
                        try {
                            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(item.createdAt))
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    Text(
                        text = dateStr,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }

                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // أزرار التحكم الفرعية (مشاركة، حذف)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة",
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { deleteDialogPresented = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * مستخرج مصغرات الفيديو (Video Thumbnail) مدمج بلغة Kotlin باستخدام MediaMetadataRetriever
 */
@Composable
fun VideoThumbnailExtractor(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var durationText by remember { mutableStateOf("") }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(filePath)
                    // جلب أول إطار للفيديو (عند 1 ثانية لمقاومة السواد)
                    val bitmap = retriever.getFrameAtTime(1000000)
                    frameBitmap = bitmap

                    // جلب مدة التشغيل
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (durationMs > 0) {
                        val minutes = (durationMs / 1000) / 60
                        val seconds = (durationMs / 1000) % 60
                        durationText = String.format("%02d:%02d", minutes, seconds)
                    }
                    retriever.release()
                }
            } catch (e: Exception) {
                Log.e("MetadataExtractor", "فصل استخراج معاينة الفيديو: ${e.message}")
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap!!.asImageBitmap(),
                contentDescription = "معاينة",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF8B5CF6).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6).copy(alpha = 0.7f),
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        if (durationText.isNotEmpty()) {
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

private fun getIconAndColorForFileType(type: FileType): Pair<ImageVector, Color> {
    return when (type) {
        FileType.AUDIO -> Icons.Default.AudioFile to Color(0xFF10B981)
        FileType.DOCUMENT -> Icons.Default.Description to Color(0xFF3B82F6)
        FileType.ARCHIVE -> Icons.Default.FolderZip to Color(0xFFF59E0B)
        FileType.APK -> Icons.Default.Android to Color(0xFF84CC16)
        else -> Icons.Default.Extension to Color(0xFF6B7280)
    }
}

/**
 * فتح ملف التنزيل عبر مزود FileProvider ونظام التشغيل المناسب
 */
fun openDownloadedFile(context: Context, item: DownloadItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "الرجاء العلم أنه تم نقل الملف أو حذفه من القرص.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("FileOpener", "فشل تشغيل الملف: ${e.message}")
        // المحاولة الثانية في حالة عدم توفر برامج تشغيل لموجب المايم
        try {
            val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
        } catch (x: Exception) {
            Toast.makeText(context, "لا تتوفر تطبيقات لفتح هذا النوع من الملفات.", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * مشاركة الملف مع برامج وتطبيقات النظام
 */
fun shareDownloadedFile(context: Context, item: DownloadItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "الملف غير متاح للمشاركة حالياً.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "شارك الملف عبر:")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل بدء قناة المشاركة الخادمية: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "قيد المعاينة"
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
