package com.example.nebula.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A playlist. Local-first: there is no YouTube Music identity here, so `id` is a
 * local autoincrement key rather than a browseId. Adding a nullable `browseId`
 * later, if sync is ever wanted, is a one-column migration.
 *
 * [isSystem] marks the one built-in playlist that mirrors the download index. It is
 * undeletable and is found by this flag rather than by name, so renaming it cannot
 * break the sync that keeps it up to date.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false
)
