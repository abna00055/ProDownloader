package com.example.ui.detail

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.download.DownloadItem
import com.example.data.download.DownloadStatus
import com.example.data.download.FileType
import com.example.ui.download.DownloadViewModel
import com.example.ui.download.formatBytes
import com.example.ui.download.formatETA
import com.example.ui.download.formatSpeed
import com.example.ui.download.openDownloadedFile
import com.example.ui.download.shareDownloadedFile
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

/**
 * شاشة استعراض تفاصيل عملية التحميل بالكامل (Download Detail Screen).
 * تدعم:
 * - رسم مؤشر دائري فائق الدقة باستخدام الـ Canvas.
 * - رسم خط بياني حيّ (Line Graph Canvas) لسرعة التحميل.
 * - تفاصيل وعناصر التحكم اللحظية (Pause, Resume, Cancel, Delete).
 * - احتفالية نجاح مخصصة عند وصول التنزيل إلى 100% (ألوان متحركة وتطاير Confetti).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    downloadId: Long,
    viewModel: DownloadViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val item = downloads.find { it.id == downloadId }

    if (item == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Text("عذراً، لم يتم العثور على ملف التنزيل المختار.", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                Button(onClick = onNavigateBack, shape = RoundedCornerShape(12.dp)) {
                    Text("العودة للتنزيلات")
                }
            }
        }
        return
    }

    // حساب نسبة التحميل
    val progress = if (item.fileSize > 0) {
        (item.downloadedBytes.toDouble() / item.fileSize.toDouble()).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
    val percentText = String.format("%.1f%%", progress * 100)

    // تتبع تاريخ السرعة لرسم المخطط البياني (خطي مستمر لـ 20 نقطة تغذية برمجية)
    val speedHistory = remember(item.id) { mutableStateListOf<Long>() }

    // مؤقت ومراقب لتحديث السرعة في اللائحة
    LaunchedEffect(item.speed, item.status) {
        if (item.status == DownloadStatus.DOWNLOADING) {
            // إضافة السرعة الحالية
            speedHistory.add(item.speed)
            if (speedHistory.size > 25) {
                speedHistory.removeAt(0)
            }
        } else {
            // في حالة التوقف، يتم تسديد الخط بقيم صفرية لتمثيل الخمود
            if (speedHistory.isEmpty()) {
                speedHistory.addAll(List(15) { 0L })
            } else if (item.speed == 0L) {
                speedHistory.add(0L)
                if (speedHistory.size > 25) {
                    speedHistory.removeAt(0)
                }
            }
        }
    }

    // محرك احتفالية التنزيل المكتمل بنجاح (Confetti Engine)
    val isCompleted = item.status == DownloadStatus.COMPLETED
    var confettiTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            confettiTrigger = true
        }
    }

    // تدرج الألوان الرئيسي للباقة
    val mainGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "تفاصيل تنزيل الملف",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // مشاركة الملف إن كان جاهزاً ومكتملاً
                    if (isCompleted) {
                        IconButton(onClick = {
                            shareDownloadedFile(context, File(item.filePath), item.mimeType)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. استعراض Thumbnail للفيديو أو معاينة للملفات الأخرى
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // أيقونة ضخمة أو رندر للملف
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.fileType == FileType.VIDEO && isCompleted) {
                            // محاولة تحميل الصورة من الملف المحلي للفيديو عبر Coil
                            AsyncImage(
                                model = File(item.filePath),
                                contentDescription = "معاينة الفيديو",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val icon = when (item.fileType) {
                                FileType.VIDEO -> Icons.Default.PlayCircle
                                FileType.AUDIO -> Icons.Default.MusicNote
                                FileType.ARCHIVE -> Icons.Default.FolderZip
                                FileType.DOCUMENT -> Icons.Default.Description
                                FileType.APK -> Icons.Default.Android
                                FileType.OTHER -> Icons.Default.InsertDriveFile
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "صيغة المحتوى: ${item.fileType}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "رابط المصدر: ${item.url}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. مؤشر مخصص عائم للتنزيل المكتبي والدائري (Canvas View)
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                // رسم التقدم الدائري بواسطة الـ Canvas مع تدرج لوني فخم
                val progressAnim by animateFloatAsState(
                    targetValue = progress.toFloat(),
                    animationSpec = tween(500, easing = LinearOutSlowInEasing),
                    label = "circular_progress"
                )

                Canvas(modifier = Modifier.size(170.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val centerOffset = Offset(size.width / 2, size.height / 2)
                    val radius = (size.width - strokeWidth) / 2

                    // دائرة في الخلفية
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.25f),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )

                    // قوس التقدم الملون المنحني
                    drawArc(
                        brush = mainGradient,
                        startAngle = -90f,
                        sweepAngle = progressAnim * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        size = Size(radius * 2, radius * 2),
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                    )
                }

                // نصوص النسبة وجاهزية الملف
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = percentText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF8B5CF6),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (item.status) {
                            DownloadStatus.DOWNLOADING -> "قيد التنزيل..."
                            DownloadStatus.PAUSED -> "متوقف مؤقتاً"
                            DownloadStatus.COMPLETED -> "مكتمل وآمن"
                            DownloadStatus.QUEUED -> "في الانتظار"
                            DownloadStatus.CANCELLED -> "ملغي"
                            DownloadStatus.FAILED -> "فشل التحميل"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.COMPLETED -> Color(0xFF10B981)
                            DownloadStatus.PAUSED -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }

                // عرض المؤدية الاحتفالية Confetti عند الاكتمال
                if (confettiTrigger) {
                    CelebrateConfettiCanvas()
                }
            }

            // 3. الخط البياني لسرعة التحميل اللحظي (Canvas Line Graph)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مخطط سرعة التحميل اللحظي",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = formatSpeed(item.speed),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFF8B5CF6)
                        )
                    }

                    // خط الرسم بواسطة الكانفس
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        if (speedHistory.size > 1) {
                            val maxSpeed = (speedHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L)
                            val widthInterval = size.width / (speedHistory.size - 1)
                            val path = Path()

                            speedHistory.forEachIndexed { index, sp ->
                                val x = index * widthInterval
                                val progressRatio = sp.toDouble() / maxSpeed.toDouble()
                                val y = (size.height - (progressRatio * size.height)).toFloat()

                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            // رسم الخط البياني بتجويف متعرج ناعم
                            drawPath(
                                path = path,
                                brush = mainGradient,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // رسم مسار تعبئة شفاف تحت المنحنى
                            path.lineTo(size.width, size.height)
                            path.lineTo(0f, size.height)
                            path.close()
                            drawPath(
                                path = path,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF8B5CF6).copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                        } else {
                            // رندر لخط خامل في الخلفية عند انعدام السجلات الكافية
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.2f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(size.width, size.height / 2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("السرعة المقاسة", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        Text("الزمن اللحظي", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // 4. كرت البيانات التفصيلية المكتوبة
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(label = "الحجم الكلي", value = formatBytes(item.fileSize))
                    DetailRow(label = "تم تحميل", value = formatBytes(item.downloadedBytes))
                    DetailRow(label = "عدد قنوات الاتصال", value = "${item.threadCount} خيوط تفرعية")
                    DetailRow(label = "مسار الحفظ بالقرص", value = item.filePath, isUrl = true)
                    DetailRow(label = "الزمن المتبقي (ETA)", value = formatETA(item.etaSeconds))
                }
            }

            // 5. منصة التحكم والأزرار الفورية المدهشة (Pause, Resume, Cancel, Open)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == DownloadStatus.DOWNLOADING) {
                    Button(
                        onClick = { viewModel.pauseDownload(item.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إيقاف مؤقت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.QUEUED) {
                    Button(
                        onClick = { viewModel.resumeDownload(item.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استئناف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.QUEUED) {
                    Button(
                        onClick = {
                            viewModel.cancelDownload(item.id)
                            Toast.makeText(context, "تم إلغاء عملية التحميل وحذف كتل الملف المؤقت", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إلغاء تماماً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (isCompleted) {
                    Button(
                        onClick = { openDownloadedFile(context, File(item.filePath), item.mimeType) },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("افتح الملف المعالج", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // زر الحذف النهائي من التطبيق والقرص
                IconButton(
                    onClick = {
                        viewModel.deleteDownload(item)
                        Toast.makeText(context, "تم إقصاء السجل من قاعدة البيانات وحذف السجلات بنجاح", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف نهائي", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * صف لعرض البيانات كبطاقة مفصلة منسقة
 */
@Composable
fun DetailRow(label: String, value: String, isUrl: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            fontSize = if (isUrl) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isUrl) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            maxLines = if (isUrl) 3 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * احتفالية تطاير Confetti مخصصة مرسومة بسلاسة بالـ Canvas وعداد الـ Animation
 */
@Composable
fun CelebrateConfettiCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val translationY by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_y"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_rot"
    )

    // بذور عشوائية لـ 20 قصاصة متناثرة
    val items = remember {
        List(20) {
            ConfettiItem(
                color = Color(
                    red = Random.nextInt(100, 255),
                    green = Random.nextInt(100, 255),
                    blue = Random.nextInt(100, 255)
                ),
                startX = Random.nextFloat() * 190,
                radius = Random.nextFloat() * 8 + 6,
                isCircle = Random.nextBoolean()
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
    ) {
        items.forEachIndexed { index, conf ->
            val yOffset = (translationY + (index * 12)) % size.height
            val xOffset = (conf.startX + (Math.sin(rotation.toDouble() / 15 + index) * 20)).toFloat()

            if (conf.isCircle) {
                drawCircle(
                    color = conf.color,
                    radius = conf.radius,
                    center = Offset(xOffset, yOffset)
                )
            } else {
                drawRect(
                    color = conf.color,
                    topLeft = Offset(xOffset - conf.radius, yOffset - conf.radius),
                    size = Size(conf.radius * 2, conf.radius * 1.5f)
                )
            }
        }
    }
}

data class ConfettiItem(
    val color: Color,
    val startX: Float,
    val radius: Float,
    val isCircle: Boolean
)
