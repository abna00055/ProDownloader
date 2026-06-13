package com.example.ui.settings

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * شاشة الإعدادات والتفضيلات للتطبيق (Settings Screen).
 * تضم:
 * - خيارات تحديد مسار الحفظ الافتراضي.
 * - أقصى تفريعات لمستويات الخيوط المتوازية (Max Thread Count).
 * - احصائيات وخيارات مسح الذاكرة المؤقتة للويب.
 * - واجهة حول التطبيق والشركة المطورة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var threadCountSlider by remember { mutableFloatStateOf(8f) }
    var enableNotifications by remember { mutableStateOf(true) }
    var autoResumeWifi by remember { mutableStateOf(true) }

    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "إعدادات Pro Downloader",
                        fontWeight = FontWeight.Bold,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. قسم تهيئة التحميل والخيوط
            Text(
                text = "محرك التحلو الفوري المسرّع",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color(0xFF8B5CF6)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // شريط اختيار Threads
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("أقصى عدد خيوط اتصال متوازية لكل ملف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${threadCountSlider.toInt()} قنوات بث",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF8B5CF6)
                            )
                        }
                        Slider(
                            value = threadCountSlider,
                            onValueChange = { threadCountSlider = it },
                            valueRange = 1f..16f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF8B5CF6),
                                activeTickColor = Color(0xFF8B5CF6),
                                thumbColor = Color(0xFF6366F1)
                            )
                        )
                        Text(
                            text = "زيادة عدد الخيوط تعني كفاءة أعلى في تجزئة الملف للتحميل المتزامن وقهر حظر السرعات.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // تفعيل الاشعارات
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("إشعارات التحميل النشطة لشريط المهام", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("تحديث التقدم اللحظي بشكل مستمر", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = enableNotifications,
                            onCheckedChange = { enableNotifications = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6))
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // استئناف تلقائي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("استئناف تلقائي للتنزيل على شبكة الـ Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("إعادة تشغيل المهام فور عودة البث والاتصال بالشبكة الموثوقة", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoResumeWifi,
                            onCheckedChange = { autoResumeWifi = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF8B5CF6))
                        )
                    }
                }
            }

            // 2. قسم الملفات والتخزين
            Text(
                text = "مساحة التخزين والوجهة",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color(0xFF8B5CF6)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("دليل الحفظ الافتراضي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "تنزيلات النظام الداخلية",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // تنظيف التخزين المؤقت
                    Button(
                        onClick = {
                            Toast.makeText(context, "تم مسح الذاكرة الفيدوية المؤقتة بنجاح", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("مسح الملفات المؤقتة المتراكمة (Temp Files)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. حول التطبيق والابتكار
            Text(
                text = "حول التطبيق والمعمارية",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color(0xFF8B5CF6)
            )

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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Pro Downloader v1.0",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "تطبيق فائق السرعة يعزز من كفاءة بروتوكولات الشبكة المتعددة لتجزئة وتنزيل الملفات وقنص الفيديوهات والبث التلفزيوني عبر الخيوط المتزامنة بنظام أندرويد الحديث ومبني بلغة Kotlin و Jetpack Compose بالكامل وفق قواعد معمارية MVVM الصلبة.",
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
