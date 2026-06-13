package com.example.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * واجهة برمجية للوصول إلى بيانات التنزيلات في قاعدة بيانات Room.
 */
@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem): Long

    @Update
    suspend fun update(item: DownloadItem)

    @Delete
    suspend fun delete(item: DownloadItem)

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadItem?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadByIdFlow(id: Long): Flow<DownloadItem?>

    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'QUEUED'")
    fun getActiveDownloadsSync(): List<DownloadItem>
}
