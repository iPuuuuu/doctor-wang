package com.ipuuuuu.doctorwang.data.model

data class UserProfile(
    val userId: String,
    val name: String,
    val age: Int,
    val height: Int,
    val weight: Float,
    val historyDiseases: List<String>,
    val records: List<HealthRecord>,
)

data class HealthRecord(
    val date: String,
    val type: String,
)
