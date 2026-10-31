package com.agendalavanda.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val section: String,
    val title: String,
    val body: String = "",
    val date: String = "",
    val done: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
