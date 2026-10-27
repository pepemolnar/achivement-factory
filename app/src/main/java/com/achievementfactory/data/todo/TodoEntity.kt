package com.achievementfactory.data.todo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A one-off todo (TODO-01), assigned to exactly one period instance: [periodType] plus the
 * [periodKey] that was current when it was created or restored (same key format as `progress`).
 *
 * There is deliberately no `archived` column. Whether a todo is "in its tab" or "expired into the
 * archive" (TODO-04) is *derived* on read: it is in its tab while [periodKey] is the current key
 * for its type, and an unfinished todo whose key is no longer current is expired. Nothing has to
 * run when a period ends — no maintenance pass, no background worker — so expiry is correct after
 * any absence, however long (the same self-healing property as streak reconciliation). A finished
 * todo from a closed period is kept but shown nowhere.
 */
@Entity(
    tableName = "todos",
    indices = [Index(value = ["periodType", "periodKey"])],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val periodType: String, // "DAILY" | "WEEKLY" | "MONTHLY"
    val periodKey: String,
    val done: Boolean = false,
    val createdAtEpochDay: Long,
    val completedAtEpochDay: Long? = null,
)
