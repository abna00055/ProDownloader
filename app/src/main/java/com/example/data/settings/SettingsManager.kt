package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

// Delegate extension for creating DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_downloader_settings")

/**
 * مدير الإعدادات والتفضيلات للتطبيق باستخدام DataStore Preferences.
 */
class SettingsManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Keys
        val DEFAULT_THREAD_COUNT = intPreferencesKey("default_thread_count")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val AUTO_START_ON_OPEN = booleanPreferencesKey("auto_start_on_open")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val DEFAULT_DOWNLOAD_PATH = stringPreferencesKey("default_download_path")
        val AUTO_ORGANIZE_FILES = booleanPreferencesKey("auto_organize_files")
        
        val NOTIF_ACTIVE = booleanPreferencesKey("notif_active")
        val NOTIF_COMPLETED = booleanPreferencesKey("notif_completed")
        val NOTIF_FAILED = booleanPreferencesKey("notif_failed")
        val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
        val NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        
        val THEME_MODE = stringPreferencesKey("theme_mode") // "System", "Light", "Dark"
        val ACCENT_COLOR_HEX = intPreferencesKey("accent_color_hex")
    }

    // Default save directory definition
    private val defaultDownloadDir: String by lazy {
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath 
            ?: context.filesDir.absolutePath
    }

    // Flows for reading settings
    val threadCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_THREAD_COUNT] ?: 4
    }

    val maxConcurrentDownloadsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_CONCURRENT_DOWNLOADS] ?: 3
    }

    val autoStartOnOpenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_START_ON_OPEN] ?: false
    }

    val wifiOnlyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WIFI_ONLY] ?: false
    }

    val defaultDownloadPathFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_DOWNLOAD_PATH] ?: defaultDownloadDir
    }

    val autoOrganizeFilesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_ORGANIZE_FILES] ?: true
    }

    val notifActiveFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_ACTIVE] ?: true
    }

    val notifCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_COMPLETED] ?: true
    }

    val notifFailedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_FAILED] ?: true
    }

    val notifSoundFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_SOUND] ?: true
    }

    val notifVibrateFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIF_VIBRATE] ?: true
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "System"
    }

    val accentColorHexFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR_HEX] ?: 0xFF8B5CF6.toInt()
    }

    // Storage utility: Current used size
    fun getAppUsedStorageBytes(): Long {
        return try {
            val totalDir = context.filesDir
            val extDir = context.getExternalFilesDir(null)
            getFolderSize(totalDir) + (extDir?.let { getFolderSize(it) } ?: 0L)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { subFile ->
            size += getFolderSize(subFile)
        }
        return size
    }

    fun clearCacheFiles(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            val extCacheDir = context.externalCacheDir
            deleteFolderContents(cacheDir)
            if (extCacheDir != null) deleteFolderContents(extCacheDir)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteFolderContents(file: File) {
        file.listFiles()?.forEach { subItem ->
            if (subItem.isDirectory) {
                deleteFolderContents(subItem)
            }
            subItem.delete()
        }
    }

    // Setters
    suspend fun setThreadCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_THREAD_COUNT] = count.coerceIn(1, 8)
        }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5)
        }
    }

    suspend fun setAutoStartOnOpen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_ON_OPEN] = enabled
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WIFI_ONLY] = enabled
        }
    }

    suspend fun setDefaultDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_DOWNLOAD_PATH] = path
        }
    }

    suspend fun setAutoOrganizeFiles(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_ORGANIZE_FILES] = enabled
        }
    }

    suspend fun setNotifActive(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIF_ACTIVE] = enabled
        }
    }

    suspend fun setNotifCompleted(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIF_COMPLETED] = enabled
        }
    }

    suspend fun setNotifFailed(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIF_FAILED] = enabled
        }
    }

    suspend fun setNotifSound(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIF_SOUND] = enabled
        }
    }

    suspend fun setNotifVibrate(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIF_VIBRATE] = enabled
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setAccentColor(color: Int) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_HEX] = color
        }
    }
}
