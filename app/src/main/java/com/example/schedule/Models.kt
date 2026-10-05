package com.example.schedule

import kotlinx.serialization.Serializable

@Serializable
enum class WeekType { ODD, EVEN }

@Serializable
data class SubgroupPair(
    val index: Int,       // 1 или 2
    val subject: String,
    val teacher: String,
    val room: String
)

@Serializable
data class Pair(
    val number: Int,
    val time: String,
    val day: String,
    val week: WeekType,
    val subgroups: List<SubgroupPair>
)

@Serializable
data class SavedSchedule(
    val pairs: List<Pair>,
    val importedAt: Long
)
