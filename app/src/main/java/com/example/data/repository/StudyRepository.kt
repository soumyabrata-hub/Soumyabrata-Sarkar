package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class StudyRepository(
    private val subjectDao: SubjectDao,
    private val studySessionDao: StudySessionDao,
    private val userProfileDao: UserProfileDao
) {
    val allSubjects: Flow<List<Subject>> = subjectDao.getAllSubjects()
    val allSessions: Flow<List<StudySession>> = studySessionDao.getAllSessions()
    val userProfile: Flow<UserProfile?> = userProfileDao.getUserProfileFlow()
    val totalStudyDuration: Flow<Long?> = studySessionDao.getTotalStudyDurationFlow()
    val totalXpEarned: Flow<Int?> = studySessionDao.getTotalXpEarnedFlow()

    // Pre-populate database with default values if they are empty
    suspend fun populateDefaultsIfEmpty() {
        val subjects = allSubjects.firstOrNull() ?: emptyList()
        if (subjects.isEmpty()) {
            val defaultSubjects = listOf(
                Subject(name = "Mathematics", colorHex = "#FF4B4B", iconName = "Calculate"),
                Subject(name = "Computer Science", colorHex = "#2196F3", iconName = "Computer"),
                Subject(name = "Science & Physics", colorHex = "#4CAF50", iconName = "Science"),
                Subject(name = "Literature / Languages", colorHex = "#9C27B0", iconName = "Book"),
                Subject(name = "Arts & Creativity", colorHex = "#FF9800", iconName = "Brush"),
                Subject(name = "General Practice", colorHex = "#795548", iconName = "Timer")
            )
            for (subj in defaultSubjects) {
                subjectDao.insertSubject(subj)
            }
        }

        val profile = userProfileDao.getUserProfile()
        if (profile == null) {
            userProfileDao.insertOrUpdateProfile(UserProfile(
                id = 1,
                level = 1,
                totalXp = 0,
                companionName = "PomoBud",
                companionStage = 1,
                streakDays = 0,
                lastStudyTimestamp = 0L
            ))
        }
    }

    suspend fun addSubject(name: String, colorHex: String, iconName: String): Long {
        val subject = Subject(name = name, colorHex = colorHex, iconName = iconName)
        return subjectDao.insertSubject(subject)
    }

    suspend fun deleteSubject(subject: Subject) {
        subjectDao.deleteSubject(subject)
    }

    suspend fun deleteSession(sessionId: Int) {
        studySessionDao.deleteSessionById(sessionId)
    }

    /**
     * Logic for complete session creation including gamified profile XP additions, leveling,
     * companion growth stage transitions, and day streaks calculation.
     */
    suspend fun saveStudySession(
        subjectId: Int,
        subjectName: String,
        subjectColorHex: String,
        durationSeconds: Long,
        timerType: String
    ) {
        // 1. Calculate XP (Base: 1 XP per 5 seconds, plus type bonuses)
        val baseBytesXp = (durationSeconds / 5).toInt().coerceAtLeast(1)
        val typeBonus = when (timerType) {
            "POMODORO" -> 50 // Pomodoro completions provide a solid bonus
            "CUSTOM" -> 15
            else -> 0
        }
        val xpEarned = baseBytesXp + typeBonus

        // 2. Insert Session Record
        val session = StudySession(
            subjectId = subjectId,
            subjectName = subjectName,
            subjectColorHex = subjectColorHex,
            durationSeconds = durationSeconds,
            timerType = timerType,
            xpEarned = xpEarned
        )
        studySessionDao.insertSession(session)

        // 3. Update User Profile XP/Streak/Leveling Flow
        val currentProfile = userProfileDao.getUserProfile() ?: UserProfile(id = 1)
        
        val newXp = currentProfile.totalXp + xpEarned
        
        // Dynamic Level Calculation: Level formula: Need Level * 250 XP
        var nextLevelNeededXp = currentProfile.level * 250
        var evaluatedLevel = currentProfile.level
        var remainingXp = newXp
        
        // Level up loop
        while (remainingXp >= (evaluatedLevel * 250)) {
            remainingXp -= (evaluatedLevel * 250)
            evaluatedLevel++
        }

        // Determine Evolving companion stage based on level thresholds
        val companionStage = when {
            evaluatedLevel >= 15 -> 4 // Stage 4: Zenith Companion
            evaluatedLevel >= 8 -> 3  // Stage 3: Elite Guardian
            evaluatedLevel >= 4 -> 2  // Stage 2: Energetic Hatchling
            else -> 1                 // Stage 1: Sleepy Core Egg
        }

        // Streak Days calculations
        val lastTime = currentProfile.lastStudyTimestamp
        val streak = calculateUpdatedStreak(lastTime, currentProfile.streakDays)

        val updatedProfile = currentProfile.copy(
            level = evaluatedLevel,
            totalXp = newXp,
            companionStage = companionStage,
            streakDays = streak,
            lastStudyTimestamp = System.currentTimeMillis()
        )
        userProfileDao.insertOrUpdateProfile(updatedProfile)
    }

    private fun calculateUpdatedStreak(lastStudyTimestamp: Long, currentStreakDays: Int): Int {
        if (lastStudyTimestamp == 0L) return 1

        val lastCal = Calendar.getInstance().apply { timeInMillis = lastStudyTimestamp }
        val nowCal = Calendar.getInstance()

        // Simplify streak check to calendar difference
        val diffDays = ((nowCal.timeInMillis - lastCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            diffDays == 0 -> {
                // Studied on same solar day => Keep same streak count
                currentStreakDays.coerceAtLeast(1)
            }
            diffDays == 1 -> {
                // Consecutive day => Increment streak count
                currentStreakDays + 1
            }
            else -> {
                // Day missed => Reset streak count
                1
            }
        }
    }
}
