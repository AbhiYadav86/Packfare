package com.packfare.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Things that aren't tied to a trip: the document vault and Explore's offline cache. */
@Dao
interface ExtrasDao {

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun documents(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun documentsForTrip(tripId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun document(id: Long): DocumentEntity?

    @Insert
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("SELECT * FROM place_cache WHERE title = :title")
    suspend fun cachedPlace(title: String): PlaceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cachePlace(place: PlaceCacheEntity)

    @Query("SELECT * FROM place_cache ORDER BY fetchedAt DESC LIMIT 20")
    fun recentPlaces(): Flow<List<PlaceCacheEntity>>
}
