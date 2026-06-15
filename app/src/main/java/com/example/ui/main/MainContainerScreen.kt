package com.example.ui.main

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import com.example.ui.browser.BrowserScreen
import com.example.ui.detail.DownloadDetailScreen
import com.example.ui.download.DownloadScreen
import com.example.ui.download.DownloadViewModel
import com.example.ui.files.FilesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.onboarding.OnboardingScreen

/**
 * شاشة هيكلة الملاحة والمحتوى الرئيسي (Main Container Screen).
 * تدير:
 * - شريان التنقل السفلي المطور (BottomNavigationBar) للتنقل السلس بين شاشات (Home, Browser, Settings).
 * - رسم كرت الملاحة (NavHost) ومعالجة مسارت الشاشات الفرعية مثل (DownloadDetailScreen).
 * - تخصيص الملاحة وتأثيرات الانتقال.
 */
@Composable
fun MainContainerScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    var isOnboardingFinished by remember {
        val prefs = context.getSharedPreferences("pro_downloader_prefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean("onboarding_finished", false))
    }

    if (!isOnboardingFinished) {
        OnboardingScreen(
            onFinished = {
                val prefs = context.getSharedPreferences("pro_downloader_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("onboarding_finished", true).apply()
                isOnboardingFinished = true
            },
            modifier = modifier
        )
    } else {
        // تتبع حالة الصفحة الحالية لتحديد الشاشة في شريط المهام السفلي
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // قائمة الشاشات الأساسية المصنفة لتحديد ما إذا كان يجب إظهار شريط التنقل السفلي
        val mainRoutes = listOf("downloads", "browser", "files", "settings")
        val shouldShowBottomBar = currentRoute in mainRoutes

        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (shouldShowBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        // 1. تبويب التنزيلات (Home)
                        NavigationBarItem(
                            selected = currentRoute == "downloads",
                            onClick = {
                                if (currentRoute != "downloads") {
                                    navController.navigate("downloads") {
                                        popUpTo("downloads") { inclusive = true }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "downloads") Icons.Filled.Download else Icons.Outlined.Download,
                                    contentDescription = "التنزيلات"
                                )
                            },
                            label = { Text("التنزيلات", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8B5CF6),
                                selectedTextColor = Color(0xFF8B5CF6),
                                indicatorColor = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            )
                        )

                        // 2. تبويب المتصفح والـ Snipping
                        NavigationBarItem(
                            selected = currentRoute == "browser",
                            onClick = {
                                if (currentRoute != "browser") {
                                    navController.navigate("browser") {
                                        popUpTo("downloads") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "browser") Icons.Filled.Language else Icons.Outlined.Language,
                                    contentDescription = "المتصفح"
                                )
                            },
                            label = { Text("المتصفح", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8B5CF6),
                                selectedTextColor = Color(0xFF8B5CF6),
                                indicatorColor = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            )
                        )

                        // 3. تبويب الملفات المحملة والمنظمة
                        NavigationBarItem(
                            selected = currentRoute == "files",
                            onClick = {
                                if (currentRoute != "files") {
                                    navController.navigate("files") {
                                        popUpTo("downloads") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "files") Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = "الملفات"
                                )
                            },
                            label = { Text("الملفات", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8B5CF6),
                                selectedTextColor = Color(0xFF8B5CF6),
                                indicatorColor = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            )
                        )

                        // 4. تبويب الإعدادات ومحرك الخيوط
                        NavigationBarItem(
                            selected = currentRoute == "settings",
                            onClick = {
                                if (currentRoute != "settings") {
                                    navController.navigate("settings") {
                                        popUpTo("downloads") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "الإعدادات"
                                )
                            },
                            label = { Text("الإعدادات", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8B5CF6),
                                selectedTextColor = Color(0xFF8B5CF6),
                                indicatorColor = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "downloads",
                modifier = Modifier.padding(innerPadding)
            ) {
                // شاشة التنزيلات (الرئيسية)
                composable("downloads") {
                    DownloadScreen(
                        viewModel = viewModel,
                        onNavigateToDetail = { downloadId ->
                            navController.navigate("detail/$downloadId")
                        },
                        onNavigateToBrowser = {
                            navController.navigate("browser") {
                                popUpTo("downloads") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // شاشة المتصفح الذكي وقانص الفيديو
                composable("browser") {
                    BrowserScreen(
                        downloadViewModel = viewModel,
                        onNavigateToDownloads = {
                            navController.navigate("downloads") {
                                popUpTo("downloads") { inclusive = true }
                            }
                        }
                    )
                }

                // شاشة الملفات والفرز والوصول
                composable("files") {
                    FilesScreen(viewModel = viewModel)
                }

                // شاشة تفضيلات التطبيق وخيوط التحميل
                composable("settings") {
                    SettingsScreen()
                }

                // شاشة تفصيلية لكل ملف تنزيل
                composable(
                    route = "detail/{downloadId}",
                    arguments = listOf(
                        navArgument("downloadId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val downloadId = backStackEntry.arguments?.getLong("downloadId") ?: 0L
                    DownloadDetailScreen(
                        downloadId = downloadId,
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
