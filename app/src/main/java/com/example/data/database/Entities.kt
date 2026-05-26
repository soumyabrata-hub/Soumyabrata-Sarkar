package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorHex: String, // Hex string for visual display styling (e.g. "#FF4081")
    val iconName: String  // Standard icon name key (e.g., "School", "Computer", "Book", "Draw")
)

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subjectId: Int,
    val subjectName: String, // Snapshot of the subject name in case it changes
    val subjectColorHex: String, // Snapshot of subject color
    val durationSeconds: Long, // Focus duration in seconds
    val timerType: String, // "POMODORO", "STOPWATCH", or "CUSTOM"
    val timestamp: Long = System.currentTimeMillis(), // Session end timestamp
    val xpEarned: Int
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Fixed ID since there's only one user profile locally
    val level: Int = 1,
    val totalXp: Int = 0,
    val companionName: String = "Focus Buddy",
    val companionStage: Int = 1, // Evolutionary stage of companion (1: Egg, 2: Baby, 3: Teen, 4: Master)
    val streakDays: Int = 0,
    val lastStudyTimestamp: Long = 0L
)
