package com.example.schedule

import kotlinx.serialization.Serializable

@Serializable
enum class WeekType { ODD, EVEN }

@Serializable
data class SubgroupPair(
    val index: Int,
    val subject: String,
    val teacher: String,
    val room: String
)

@Serializable
data class Lesson(
    val group: String,
    val number: Int,
    val time: String,
    val day: String,
    val week: WeekType,
    val subgroups: List<SubgroupPair>
)

@Serializable
data class SavedSchedule(
    val pairs: List<Lesson>,
    val importedAt: Long
)

@Serializable
data class Note(
    val key: String,
    val text: String,
    val createdAt: Long
)