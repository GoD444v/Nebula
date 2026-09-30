package com.example.nebula.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created playlist. Local-first: there is no YouTube Music identity here, so
 * `id` is a local autoincrement key rather than a browseId. Adding a nullable
 * `browseId` later, if sync is ever wanted, is a one-column migration.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
