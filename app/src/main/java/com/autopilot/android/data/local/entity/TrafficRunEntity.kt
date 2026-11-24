package com.autopilot.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_runs",
    indices = [Index(value = ["createdAt"]), Index(value = ["status"])]
)
data class TrafficRunEntity(
    @PrimaryKey val id: String,
    val websiteUrl: String,
    val plannedEvents: Int,
    val mode: String,
    val speed: String,
    val concurrency: Int,
    val pagePathsJson: String,
    val status: String,
    val submitted: Int,
    val failed: Int,
    val lastError: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?
)
