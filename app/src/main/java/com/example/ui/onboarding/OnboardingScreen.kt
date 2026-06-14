package com.example.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // We have 4 slides: Speed, Control, Organization, Permissions
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    val slides = listOf(
        OnboardingPage(
            title = "حمّل بسرعة خارقة",
            description = "تقنية التقسيم متعدد القنوات الذكي تتيح لك استغلال كامل سرعة الإنترنت لتنزيل الملفات بوقت قياسي وبدون بطء.",
            icon = Icons.Default.Speed,
            colors = listOf(Color(0xFF8B5CF6), Color(0xFFC084FC))
        ),
        OnboardingPage(
            title = "تحكم كامل وتام",
            description = "توقف مؤقتاً، استأنف، أو إلغِ أي تنزيل في أي وقت بكل مرونة وسهولة، مع حماية الجلسة من التوقف أو التعطل.",
            icon = Icons.Default.Tune,
            colors = listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))
        ),
        OnboardingPage(
            title = "نظّم ملفاتك تلقائياً",
            description = "فرز وتصنيف ذكي للملفات المحملة إلى مجلدات مخصصة (فيديو، صوت، مستندات، مضغوطة) لسهولة العثور عليها وتصفحها.",
            icon = Icons.Default.FolderZip,
            colors = listOf(Color(0xFF10B981), Color(0xFF34D399))
        ),
        OnboardingPage(
            title = "منح الأذونات اللازمة",
            description = "يحتاج التطبيق لصلاحيات الإشعارات وتخزين الملفات لضمان جودة الأداء واستئناف التحميل في الخلفية بشكل آمن.",
            icon = Icons.Default.Security,
            colors = listOf(Color(0xFFEF4444), Color(0xFFF87171))
        )
    )

    // Permission tracking
    var hasNotifyPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var hasStoragePermission by remember {
        mutableStateOf(com.example.data.download.PermissionHandler.hasStoragePermission(context))
    }

    // Auto-update permission states when user returns to onboarding screen (ON_RESUME)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = com.example.data.download.PermissionHandler.hasStoragePermission(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotifyPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifyPermission = isGranted
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasStoragePermission = com.example.data.download.PermissionHandler.hasStoragePermission(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                            listOf(Color(0xFF0F172A), Color(0xFF030712))
                        } else {
                            listOf(Color(0xFFF8FAFC), Color(0xFFF1F5F9))
                        }
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pro Downloader",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (pagerState.currentPage < 3) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(3) } }) {
                        Text("تخطي", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
                    }
                }
            }

            // Central Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Beautiful custom illustrated background circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        slide.colors[0].copy(alpha = 0.25f),
                                        slide.colors[1].copy(alpha = 0.05f)
                                    )
                                )
                            )
                    ) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = slide.colors[0],
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = slide.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = slide.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = slide.description,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    // If it is the permission page, show nice checkable permission controls
                    if (page == 3) {
                        Spacer(modifier = Modifier.height(30.dp))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PermissionRow(
                                title = "إشعارات التحميل اللحظية",
                                description = "لعرض سرعة التحميل ونسبة التقدم الحية في الخلفية.",
                                isGranted = hasNotifyPermission,
                                required = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                                onGrant = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )

                            PermissionRow(
                                title = "تخزين الملفات بالقرص",
                                description = "لكتابة وحفظ الملفات في مجلد التنزيلات العام بجهازك وصنع مجلد مخصص.",
                                isGranted = hasStoragePermission,
                                required = true,
                                onGrant = {
                                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
                                        // لأجهزة أندرويد 11 و 12، إعطاء صلاحية الوصول الكامل اختيارياً أو طلب الأذونات القياسية
                                        try {
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            storagePermissionLauncher.launch(com.example.data.download.PermissionHandler.getRequiredPermissions())
                                        }
                                    } else {
                                        // لباقي الإصدارات، استخدم معالج الأذونات الموحد لطلب أذونات الوسائط المتعددة (أندرويد 13+) أو التخزين (أندرويد 9-)
                                        storagePermissionLauncher.launch(com.example.data.download.PermissionHandler.getRequiredPermissions())
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Actions & Pagination Dots
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    repeat(4) { idx ->
                        val isSelected = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSelected) slides[pagerState.currentPage].colors[0]
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Call to action button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinished()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = slides[pagerState.currentPage].colors[0]
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 3) "ابدأ الرحلة الآن" else "التالي",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    required: Boolean,
    onGrant: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Done else Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!isGranted && required) {
                TextButton(onClick = onGrant) {
                    Text("سماح", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "مفعّل",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
        }
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val colors: List<Color>
)
