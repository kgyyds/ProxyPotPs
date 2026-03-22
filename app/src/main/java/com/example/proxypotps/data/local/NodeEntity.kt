package com.example.proxypotps.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val localProxyHost: String,
    val localProxyPort: Int?,
    val localProxyType: String,
    val extrasJson: String,
    val status: String,
    val latencyMs: Long?,
    val statusReason: String?
)
