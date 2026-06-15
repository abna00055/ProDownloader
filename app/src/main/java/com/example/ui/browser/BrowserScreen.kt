package com.example.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.download.DownloadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

/**
 * شاشة المتصفح المدمج ومكتشف الفيديوهات الذكي (Video Sniffer).
 * تدعم:
 * - تصفح كامل برابط أو كلمات بحث.
 * - واجهة تصفح تفاعلية مع شريط تقدم تحميل الصفحة.
 * - منطق WebViewClient مخصص لاعتراض الروابط وفحص الترويسات (Headers).
 * - نافذة سفلية تعرض الفيديوهات والملفات الصوتية المكتشفة مع إمكانية تحميلها بنقرة واحدة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    downloadViewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDownloads: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var pageTitle by remember { mutableStateOf("جوجل") }
    var pageProgress by remember { mutableStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // الاستماع للرابط الممرر للتصفح التلقائي عند التحويل للـ Fallback
    val prefilledUrlState by downloadViewModel.prefilledUrlFlow.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledUrlState, webViewInstance) {
        val prefUrl = prefilledUrlState
        if (!prefUrl.isNullOrEmpty() && prefUrl.startsWith("http")) {
            urlInput = prefUrl
            currentUrl = prefUrl
            webViewInstance?.loadUrl(prefUrl)
            downloadViewModel.clearPrefilledUrl()
        }
    }

    // القائمة الذكية للملفات المكتشفة (Sniffed Media List)
    val sniffedUrlsState = remember { mutableStateListOf<SniffedMedia>() }

    // التحكم بالـ ModalBottomSheet للملفات المكتشفة
    var showSnifferSheet by remember { mutableStateOf(false) }

    var selectedM3u8Media by remember { mutableStateOf<SniffedMedia?>(null) }
    var parsedM3u8Options by remember { mutableStateOf<List<com.example.ui.download.M3u8StreamOption>?>(null) }
    var isParsingM3u8 by remember { mutableStateOf(false) }

    LaunchedEffect(showSnifferSheet) {
        if (!showSnifferSheet) {
            selectedM3u8Media = null
            parsedM3u8Options = null
            isParsingM3u8 = false
        }
    }

    // زر العودة للخلف للنظام للتنقل داخل المتصفح
    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    // تدرج لوني أزرق بنفسجي عصري للـ Primary
    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // شريط تصفح وإدخال روابط
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // أزرار ملاحة للخلف وللأمام
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = webViewInstance?.canGoBack() == true,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "للخلف",
                            tint = if (webViewInstance?.canGoBack() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    // حقل إدخال الرابق أو كلمات البحث عن الفيديو
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val processed = processedWebUrl(urlInput)
                                currentUrl = processed
                                urlInput = processed
                                webViewInstance?.loadUrl(processed)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        ),
                        placeholder = { Text("ابحث أو اكتب رابط موقع الفيديوهات...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    )

                    // زر بدء التصفح والسير إلى الموقع
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            val processed = processedWebUrl(urlInput)
                            currentUrl = processed
                            urlInput = processed
                            webViewInstance?.loadUrl(processed)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(primaryGradient)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "انطلق",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // مؤشر تقدم تحميل الويب المدمج
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF8B5CF6),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        },
        floatingActionButton = {
            // زر عائم يظهر فقط عند وجود قنوات بث وفيديو تم التجسس عليها واكتشافها بنجاح
            AnimatedVisibility(
                visible = sniffedUrlsState.isNotEmpty(),
                enter = scaleIn(animationSpec = spring()) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showSnifferSheet = true },
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(12.dp, CircleShape)
                            .background(primaryGradient, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "عرض الروابط المكتشفة",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // شارة حمراء لعرض كمية الفيديوهات الملقوطة من بروتوكولات الشبكة
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${sniffedUrlsState.size}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // واجهة المتصفح WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                pageProgress = 20
                                if (url != null) {
                                    urlInput = url
                                    currentUrl = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                if (url != null) {
                                    val title = view?.title ?: "متصفح الويب"
                                    pageTitle = title
                                    
                                    // If we are on a known video page (like YouTube watch, TikTok, Vimeo, Twitch video), 
                                    // we register the webpage itself in the sniffed list so that clicking "تحميل" on it 
                                    // will let our high-fidelity YoutubeDL extract its exact resolution choices.
                                    val cleanUrl = url.lowercase()
                                    if (cleanUrl.contains("youtube.com/watch") || cleanUrl.contains("youtu.be") || 
                                        cleanUrl.contains("vimeo.com") || cleanUrl.contains("tiktok.com") || 
                                        cleanUrl.contains("facebook.com/watch") || cleanUrl.contains("twitch.tv")
                                    ) {
                                        val cleanTitle = if (title.contains("YouTube")) title else "$title (فيديو أصلي)"
                                        val existing = sniffedUrlsState.find { it.url == url }
                                        if (existing == null) {
                                            sniffedUrlsState.add(
                                                SniffedMedia(
                                                    name = cleanTitle,
                                                    url = url,
                                                    mimeType = "رابط يوتيوب/فيديو ذكي جودة متعددة",
                                                    cookie = try { android.webkit.CookieManager.getInstance().getCookie(url) } catch (e: Exception) { null },
                                                    userAgent = view?.settings?.userAgentString
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                // فحص الرابط المباشر للمورد بشكل فوري
                                if (url != null) {
                                    val cookie = try {
                                        android.webkit.CookieManager.getInstance().getCookie(url)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val userAgent = view?.settings?.userAgentString
                                    sniffUrl(url, sniffedUrlsState, scope, cookie, userAgent)
                                }
                            }

                            // اعتراض جميع الطلبات لفحص ترويساتها للتأكد من وجود فيديوهات مدمجة
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString()
                                if (reqUrl != null) {
                                    val cookie = try {
                                        android.webkit.CookieManager.getInstance().getCookie(reqUrl)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    val userAgent = try {
                                        request?.requestHeaders?.entries
                                            ?.find { it.key.equals("user-agent", ignoreCase = true) }
                                            ?.value
                                    } catch (e: Exception) {
                                        null
                                    }
                                    sniffUrl(reqUrl, sniffedUrlsState, scope, cookie, userAgent)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }

    // ورقة استعراض الميديا المكتشفة (Sniffed Media BottomSheet)
    if (showSnifferSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSnifferSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedM3u8Media != null) {
                    // واجهة اختيار جودة البث HLS/M3U8
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedM3u8Media = null
                            parsedM3u8Options = null
                        }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "رجوع")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "جودات البث المتعددة الـ HLS",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF8B5CF6)
                            )
                            Text(
                                text = selectedM3u8Media?.name ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    if (isParsingM3u8) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF8B5CF6))
                                Text("جاري فحص ملف الـ Master Playlist واستخراج الجودات...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else if (parsedM3u8Options.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("عذراً، لم نتمكن من العثور على جودات بث منفصلة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(parsedM3u8Options!!) { option ->
                                Surface(
                                    shadowElevation = 1.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            downloadViewModel.triggerPrefilledUrl(option.url, selectedM3u8Media?.cookie, selectedM3u8Media?.userAgent)
                                            showSnifferSheet = false
                                            Toast.makeText(context, "تم تحديد الجودة المتفرعة: ${option.label}", Toast.LENGTH_SHORT).show()
                                            onNavigateToDownloads?.invoke()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "الدقة المستهدفة: ${option.resolution}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = option.url,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // القائمة العادية لروابط الميديا المكتشفة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مستكشف الفيديوهات والمحتوى الأصلي",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color(0xFF8B5CF6)
                            )
                            Text(
                                text = "تم العثور على قنوات بث للروابط المكتشفة",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // زر مسح القائمة المكتشفة بالكامل لتنظيفها
                        TextButton(onClick = {
                            sniffedUrlsState.clear()
                            showSnifferSheet = false
                            Toast.makeText(context, "تم محو ذاكرة المكتشف بنجاح", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("مسح الكل", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // قائمة السجلات المكتشفة
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sniffedUrlsState) { item ->
                            Surface(
                                shadowElevation = 1.dp,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // نوع الميديا أيقونة
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFF8B5CF6),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.url,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.mimeType.isNotEmpty()) {
                                            Text(
                                                text = "نوع الرابط: ${item.mimeType}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }

                                    // زر تشغيل التحميل عبر prefilledUrl
                                    Button(
                                        onClick = {
                                            val isM3u8Url = item.url.lowercase().contains(".m3u8") || 
                                                    item.mimeType.lowercase().contains("mpegurl") || 
                                                    item.mimeType.lowercase().contains("m3u8")
                                            if (isM3u8Url) {
                                                selectedM3u8Media = item
                                                isParsingM3u8 = true
                                                parsedM3u8Options = null
                                                scope.launch {
                                                    try {
                                                        val options = downloadViewModel.parseM3u8MasterPlaylist(item.url)
                                                        parsedM3u8Options = options
                                                    } catch (e: Exception) {
                                                        parsedM3u8Options = emptyList()
                                                    } finally {
                                                        isParsingM3u8 = false
                                                    }
                                                }
                                            } else {
                                                downloadViewModel.triggerPrefilledUrl(item.url, item.cookie, item.userAgent)
                                                showSnifferSheet = false
                                                Toast.makeText(context, "تم تجهيز الملف للإدراج بقنوات تحميل تفرعية متعددة", Toast.LENGTH_SHORT).show()
                                                onNavigateToDownloads?.invoke()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Text("تحميل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * فحص وتصليح الرابط المدخل ليتوافق مع بروتوكولات الويب الآمنة
 */
fun processedWebUrl(input: String): String {
    val clean = input.trim()
    if (clean.isEmpty()) return "https://www.google.com"
    if (clean.startsWith("http://") || clean.startsWith("https://")) {
        return clean
    }
    // إن كان يحمل طابع الرابط ولكن دون بروتوكول
    if (clean.contains(".") && !clean.contains(" ")) {
        return "https://$clean"
    }
    // وإن كان كلمات بحث عامة
    return "https://www.google.com/search?q=$clean"
}

/**
 * كائن تخزين الميديا المكتشفة (Detected Media Item)
 */
data class SniffedMedia(
    val name: String,
    val url: String,
    val mimeType: String,
    val cookie: String? = null,
    val userAgent: String? = null,
    val discoveredAt: Long = System.currentTimeMillis()
)

/**
 * فحص الرابط واعتراض وتجسس للحصل على الفيديوهات المخفية أو المباشرة بالتفصيل
 */
fun sniffUrl(
    url: String,
    sniffedList: MutableList<SniffedMedia>,
    scope: CoroutineScope,
    cookie: String? = null,
    userAgent: String? = null
) {
    val cleanUrl = url.lowercase().trim()

    // فلاتر ذكية للتخلص من الإعلانات والإشارات الوهمية والروابط غير المفيدة والأصوات القصيرة للواجهة
    val blacklist = listOf(
        "blank", "ad", "placeholder", "track", "analytics", 
        "doubleclick", "google-analytics", "facebook.com", 
        "googleads", "adsbygoogle", "telemetry", "metric", "logger",
        "success.mp3", "failure.mp3", "open.mp3", "no_input.mp3", 
        "click.mp3", "pop.mp3", "bell.mp3", "beep.mp3", "sound-effect",
        "sound_effect", "interaction", "tap.mp3", "button.mp3"
    )
    if (blacklist.any { cleanUrl.contains(it) }) {
        return
    }

    // تجاهل روابط ts الفردية الصغيرة جداً ما لم تكن بثاً مباشراً كاملاً m3u8
    if (cleanUrl.contains(".ts") || cleanUrl.endsWith(".ts")) {
        return
    }

    // 1. تصفية الروابط الشائعة التي لا نريد التقاطها كصور أو نصوص
    val skipExtensions = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", 
        ".css", ".js", ".woff", ".woff2", ".ttf", ".eot", "favicon.ico"
    )
    if (skipExtensions.any { cleanUrl.contains(it) }) {
        return
    }

    // 2. التحقق من الامتدادات الشهيرة للفيديو والبثوث المباشرة
    val videoExtensions = listOf(".mp4", ".m3u8", ".mkv", ".webm", ".avi", ".mp3", ".ogg", "stream", "video")
    val hasVideoExtension = videoExtensions.any { ext -> cleanUrl.contains(ext) }

    if (hasVideoExtension) {
        val existingUrls = sniffedList.map { it.url }.toSet()
        if (!existingUrls.contains(url)) {
            val guessedName = guessNameFromUrl(url)
            val media = SniffedMedia(
                name = guessedName,
                url = url,
                mimeType = "فيديو / بث شبكي مباشر",
                cookie = cookie,
                userAgent = userAgent
            )
            scope.launch(Dispatchers.Main) {
                val currentUrls = sniffedList.map { it.url }.toSet()
                if (!currentUrls.contains(url)) {
                    sniffedList.add(media)
                }
            }
            return
        }
    }

    // 3. التجسس والـ Sniffing المتقدم عبر HEAD Requests للروابط المشكوك بأصلها
    val existingUrls = sniffedList.map { it.url }.toSet()
    if (cleanUrl.startsWith("http") && !existingUrls.contains(url)) {
        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                val headRequest = Request.Builder().url(url).head().build()
                client.newCall(headRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type")?.lowercase() ?: ""
                        if (contentType.startsWith("video/") || contentType.startsWith("audio/") ||
                            contentType.contains("mpegurl") || contentType.contains("application/x-mpegurl") ||
                            contentType.contains("application/vnd.apple.mpegurl")
                        ) {
                            val guessedName = guessNameFromUrl(url)
                            withContext(Dispatchers.Main) {
                                val currentUrls = sniffedList.map { it.url }.toSet()
                                if (!currentUrls.contains(url)) {
                                    sniffedList.add(
                                        SniffedMedia(
                                            name = guessedName,
                                            url = url,
                                            mimeType = contentType,
                                            cookie = cookie,
                                            userAgent = userAgent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // فشل فحص الرابط، نتجاهله هدوءاً لحماية الأداء
            }
        }
    }
}

/**
 * استخراج اسم ملف تقريبي من الرابط للتسهيل على المستخدم
 */
fun guessNameFromUrl(url: String): String {
    try {
        val parsedUrl = URL(url)
        val path = parsedUrl.path
        val lastSegment = path.substringAfterLast('/')
        if (lastSegment.isNotEmpty() && lastSegment.contains(".")) {
            return lastSegment
        }
    } catch (e: Exception) {
        // تجاهل
    }
    // اسم احتياطي في حال استعصى الاستخراج
    val epoch = System.currentTimeMillis() % 100000
    return "فيديو_مكتشف_$epoch.mp4"
}
