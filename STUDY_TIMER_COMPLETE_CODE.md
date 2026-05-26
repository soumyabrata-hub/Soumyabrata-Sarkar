# Study Timer — Complete Source Code Reference

This document consolidates all source code of **Study Timer**: a gamified, beautiful Material 3 Study Time Tracker featuring a circular progress timer, custom subject tagging palettes, an XP leveling engine, evolving companion avatars, day streaks, and recent study session logs.

---

## 1. Project-wide Metadata (`/metadata.json`)
```json
{
  "name": "Study Timer",
  "description": "A gamified Study Time Tracker with custom timers, subject tags, an XP leveling system, and an evolving virtual companion.",
  "requestFramePermissions": [],
  "majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]
}
```

---

## 2. Main Entry Activity (`/app/src/main/java/com/example/MainActivity.kt`)
```kotlin
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.StudyRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.timer.TimerScreen
import com.example.ui.timer.TimerViewModel
import com.example.ui.timer.TimerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = StudyRepository(
            database.subjectDao(),
            database.studySessionDao(),
            database.userProfileDao()
        )
        val viewModelFactory = TimerViewModelFactory(repository)
        val viewModel: TimerViewModel by viewModels { viewModelFactory }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TimerScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
```

---

## 3. Database Entities (`/app/src/main/java/com/example/data/database/Entities.kt`)
```kotlin
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
```

---

## 4. Database DAOs (`/app/src/main/java/com/example/data/database/Daos.kt`)
```kotlin
package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Int): Subject?
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession): Long

    @Query("SELECT SUM(durationSeconds) FROM study_sessions")
    fun getTotalStudyDurationFlow(): Flow<Long?>

    @Query("SELECT SUM(xpEarned) FROM study_sessions")
    fun getTotalXpEarnedFlow(): Flow<Int?>

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Int)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}
```

---

## 5. Main Database Class (`/app/src/main/java/com/example/data/database/AppDatabase.kt`)
```kotlin
package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Subject::class, StudySession::class, UserProfile::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

---

## 6. Study Session Repository Flow (`/app/src/main/java/com/example/data/repository/StudyRepository.kt`)
```kotlin
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

    suspend fun saveStudySession(
        subjectId: Int,
        subjectName: String,
        subjectColorHex: String,
        durationSeconds: Long,
        timerType: String
    ) {
        // Calculate XP (Base: 1 XP per 5 seconds, plus type bonuses)
        val baseBytesXp = (durationSeconds / 5).toInt().coerceAtLeast(1)
        val typeBonus = when (timerType) {
            "POMODORO" -> 50 // Pomodoro completion bonus
            "CUSTOM" -> 15
            else -> 0
        }
        val xpEarned = baseBytesXp + typeBonus

        val session = StudySession(
            subjectId = subjectId,
            subjectName = subjectName,
            subjectColorHex = subjectColorHex,
            durationSeconds = durationSeconds,
            timerType = timerType,
            xpEarned = xpEarned
        )
        studySessionDao.insertSession(session)

        val currentProfile = userProfileDao.getUserProfile() ?: UserProfile(id = 1)
        val newXp = currentProfile.totalXp + xpEarned
        
        var evaluatedLevel = currentProfile.level
        var remainingXp = newXp
        while (remainingXp >= (evaluatedLevel * 250)) {
            remainingXp -= (evaluatedLevel * 250)
            evaluatedLevel++
        }

        val companionStage = when {
            evaluatedLevel >= 15 -> 4
            evaluatedLevel >= 8 -> 3
            evaluatedLevel >= 4 -> 2
            else -> 1
        }

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
        val diffDays = ((nowCal.timeInMillis - lastCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            diffDays == 0 -> currentStreakDays.coerceAtLeast(1)
            diffDays == 1 -> currentStreakDays + 1
            else -> 1
        }
    }
}
```

---

## 7. Study Timer View Model (`/app/src/main/java/com/example/ui/timer/TimerViewModel.kt`)
```kotlin
package com.example.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.StudySession
import com.example.data.database.Subject
import com.example.data.database.UserProfile
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TimerUiState(
    val timerType: String = "POMODORO", // POMODORO, STOPWATCH, CUSTOM
    val currentPhase: String = "WORK", // WORK, BREAK
    val timeLeftSeconds: Long = 1500L, // 25 minutes default for POMODORO
    val totalDurationSeconds: Long = 1500L,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedSubject: Subject? = null
)

class TimerViewModel(private val repository: StudyRepository) : ViewModel() {

    private val _timerState = MutableStateFlow(TimerUiState())
    val timerState: StateFlow<TimerUiState> = _timerState.asStateFlow()

    val subjects: StateFlow<List<Subject>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<StudySession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalDurationSeconds: StateFlow<Long?> = repository.totalStudyDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalXpEarned: StateFlow<Int?> = repository.totalXpEarned
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.populateDefaultsIfEmpty()
        }
        viewModelScope.launch {
            subjects.collectLatest { list ->
                if (_timerState.value.selectedSubject == null && list.isNotEmpty()) {
                    _timerState.update { it.copy(selectedSubject = list.first()) }
                }
            }
        }
    }

    fun setTimerType(type: String) {
        if (_timerState.value.isRunning) return
        timerJob?.cancel()

        val defaultSecs = when (type) {
            "POMODORO" -> 1500L // 25 Min
            "STOPWATCH" -> 0L
            "CUSTOM" -> 3600L // 60 Min default
            else -> 1500L
        }

        _timerState.update {
            it.copy(
                timerType = type,
                currentPhase = "WORK",
                timeLeftSeconds = defaultSecs,
                totalDurationSeconds = defaultSecs,
                isRunning = false,
                isPaused = false
            )
        }
    }

    fun selectSubject(subject: Subject) {
        _timerState.update { it.copy(selectedSubject = subject) }
    }

    fun startTimer() {
        if (_timerState.value.isRunning) return
        _timerState.update { it.copy(isRunning = true, isPaused = false) }

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _timerState.value
                if (current.isPaused) continue

                if (current.timerType == "STOPWATCH") {
                    _timerState.update {
                        it.copy(
                            timeLeftSeconds = it.timeLeftSeconds + 1,
                            totalDurationSeconds = it.totalDurationSeconds + 1
                        )
                    }
                } else {
                    if (current.timeLeftSeconds > 1) {
                        _timerState.update { it.copy(timeLeftSeconds = it.timeLeftSeconds - 1) }
                    } else {
                        // Sound chime & swap state / Complete
                        handleTimerCompletion()
                        break
                    }
                }
            }
        }
    }

    fun pauseTimer() {
        _timerState.update { it.copy(isPaused = true) }
    }

    fun resumeTimer() {
        _timerState.update { it.copy(isPaused = false) }
    }

    fun resetTimer() {
        timerJob?.cancel()
        val type = _timerState.value.timerType
        val defaultSecs = when (type) {
            "POMODORO" -> 1500L
            "STOPWATCH" -> 0L
            "CUSTOM" -> 3600L
            else -> 1500L
        }
        _timerState.update {
            it.copy(
                timeLeftSeconds = defaultSecs,
                totalDurationSeconds = defaultSecs,
                isRunning = false,
                isPaused = false
            )
        }
    }

    private suspend fun handleTimerCompletion() {
        timerJob?.cancel()
        val current = _timerState.value
        val durationCompleted = current.totalDurationSeconds - current.timeLeftSeconds + 1

        val subject = current.selectedSubject ?: subjects.value.firstOrNull()
        if (subject != null && durationCompleted > 0) {
            repository.saveStudySession(
                subjectId = subject.id,
                subjectName = subject.name,
                subjectColorHex = subject.colorHex,
                durationSeconds = durationCompleted,
                timerType = current.timerType
            )
        }

        if (current.timerType == "POMODORO") {
            if (current.currentPhase == "WORK") {
                // Break transition (5 minutes)
                _timerState.update {
                    it.copy(
                        currentPhase = "BREAK",
                        timeLeftSeconds = 300L,
                        totalDurationSeconds = 300L,
                        isRunning = false,
                        isPaused = false
                    )
                }
            } else {
                // Back to Focus Workout
                setTimerType("POMODORO")
            }
        } else {
            resetTimer()
        }
    }

    fun finishAndSaveStopwatch() {
        if (_timerState.value.timerType != "STOPWATCH") return
        timerJob?.cancel()

        val current = _timerState.value
        val duration = current.timeLeftSeconds // Elapsed counts upwards

        viewModelScope.launch {
            val subject = current.selectedSubject ?: subjects.value.firstOrNull()
            if (subject != null && duration > 0) {
                repository.saveStudySession(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    subjectColorHex = subject.colorHex,
                    durationSeconds = duration,
                    timerType = "STOPWATCH"
                )
            }
            resetTimer()
        }
    }

    fun createSubject(name: String, colorHex: String, iconName: String) {
        viewModelScope.launch {
            repository.addSubject(name, colorHex, iconName)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
            if (_timerState.value.selectedSubject?.id == subject.id) {
                _timerState.update { it.copy(selectedSubject = null) }
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

class TimerViewModelFactory(private val repository: StudyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

---

## 8. Study Timer Master Screen Composable (`/app/src/main/java/com/example/ui/timer/TimerScreen.kt`)
```kotlin
package com.example.ui.timer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.Subject
import com.example.data.database.StudySession
import com.example.data.database.UserProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.timerState.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var showAddSubjectDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep Slate
                        Color(0xFF1E1B4B), // Cyber Indigo
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            UserProfileHeader(userProfile = userProfile)

            Spacer(modifier = Modifier.height(12.dp))

            TimerTypeSelector(
                currentType = uiState.timerType,
                isRunning = uiState.isRunning,
                onSelectType = { viewModel.setTimerType(it) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f),
                contentAlignment = Alignment.Center
            ) {
                TimerCenterpiece(
                    uiState = uiState,
                    userProfile = userProfile,
                    onStopwatchDone = { viewModel.finishAndSaveStopwatch() }
                )
            }

            SubjectTagSection(
                subjects = subjects,
                selectedSubject = uiState.selectedSubject,
                onSelectSubject = { viewModel.selectSubject(it) },
                onAddSubjectClicked = { showAddSubjectDialog = true },
                onDeleteSubject = { viewModel.deleteSubject(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TimerControls(
                uiState = uiState,
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onResume = { viewModel.resumeTimer() },
                onReset = { viewModel.resetTimer() },
                onStopwatchDone = { viewModel.finishAndSaveStopwatch() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            RecentSessionsSection(
                sessions = sessions,
                onDeleteSession = { viewModel.deleteSession(it) },
                modifier = Modifier.weight(0.9f)
            )
        }
    }

    if (showAddSubjectDialog) {
        AddSubjectDialog(
            onDismiss = { showAddSubjectDialog = false },
            onSave = { name, color, icon ->
                viewModel.createSubject(name, color, icon)
                showAddSubjectDialog = false
            }
        )
    }
}

@Composable
fun UserProfileHeader(userProfile: UserProfile?) {
    val profile = userProfile ?: UserProfile()
    val xpRequired = profile.level * 250
    val prevLevelXp = if (profile.level > 1) (profile.level - 1) * 250 else 0
    val currentLevelXpProgress = (profile.totalXp - prevLevelXp).coerceAtLeast(0)
    val progressFraction = (currentLevelXpProgress.toFloat() / 250f).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x18FFFFFF)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LVL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = profile.level.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.companionName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${profile.totalXp} XP",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFFA5B4FC)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF818CF8),
                    trackColor = Color(0x15FFFFFF),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF312E81))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Day Study Streak",
                    tint = Color(0xFFFB923C),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${profile.streakDays}d Streak",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TimerTypeSelector(
    currentType: String,
    isRunning: Boolean,
    onSelectType: (String) -> Unit
) {
    val modes = listOf(
        "POMODORO" to "Pomodoro",
        "STOPWATCH" to "Stopwatch",
        "CUSTOM" to "Custom"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x15FFFFFF))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        modes.forEach { (type, label) ->
            val isSelected = currentType == type
            val activeBackground = if (isSelected) {
                Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)))
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(activeBackground)
                    .clickable(enabled = !isRunning) { onSelectType(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TimerCenterpiece(
    uiState: TimerUiState,
    userProfile: UserProfile?,
    onStopwatchDone: () -> Unit
) {
    val duration = if (uiState.totalDurationSeconds <= 0) 1L else uiState.totalDurationSeconds
    val elapsedFraction = if (uiState.timerType == "STOPWATCH") {
        0.5f
    } else {
        (uiState.timeLeftSeconds.toFloat() / duration).coerceIn(0f, 1f)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseEffect")
    val bouncerScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HatchlingPulse"
    )

    val totalSecs = uiState.timeLeftSeconds
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    val timeLabel = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .size(260.dp)
            .shadow(10.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val rectSize = Size(diameter, diameter)
            val centerOffset = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2
            )

            drawCircle(
                color = Color(0x10FFFFFF),
                radius = diameter / 2,
                style = Stroke(width = strokeWidth)
            )

            if (uiState.timerType != "STOPWATCH") {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF22D55E),
                            Color(0xFF38BDF8),
                            Color(0xFF818CF8),
                            Color(0xFFF43F5E),
                            Color(0xFF22D55E)
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = elapsedFraction * 360f,
                    useCenter = false,
                    topLeft = centerOffset,
                    size = rectSize,
                    style = Stroke(width = strokeWidth + 1f, cap = StrokeCap.Round)
                )
            } else {
                val spinAngle = (System.currentTimeMillis() / 15) % 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF67E8F9),
                            Color(0xFFEC4899),
                            Color(0xFF67E8F9)
                        )
                    ),
                    startAngle = spinAngle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = centerOffset,
                    size = rectSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = (-10).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0x184F46E5)),
                contentAlignment = Alignment.Center
            ) {
                CompanionAvatar(
                    stage = userProfile?.companionStage ?: 1,
                    scale = if (uiState.isRunning && !uiState.isPaused) bouncerScale else 1.0f
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = timeLabel,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            Text(
                text = if (uiState.timerType == "POMODORO" && uiState.currentPhase == "BREAK") "BREAK TIME" else "FOCUS STATE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = if (uiState.timerType == "POMODORO" && uiState.currentPhase == "BREAK") Color(0xFF10B981) else Color(0xFFF43F5E)
            )
        }
    }
}

@Composable
fun CompanionAvatar(stage: Int, scale: Float) {
    Box(
        modifier = Modifier
            .size(68.dp * scale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)

            when (stage) {
                1 -> {
                    drawOval(
                        color = Color(0xFFFEF08A),
                        topLeft = Offset(center.x - r * 0.7f, center.y - r * 0.9f),
                        size = Size(r * 1.4f, r * 1.8f)
                    )
                    drawCircle(Color(0xFFFACC15), r * 0.15f, Offset(center.x - r * 0.3f, center.y - r * 0.1f))
                    val eyeY = center.y + r * 0.1f
                    drawLine(Color(0xFF713F12), Offset(center.x - r * 0.45f, eyeY), Offset(center.x - r * 0.2f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF713F12), Offset(center.x + r * 0.2f, eyeY), Offset(center.x + r * 0.45f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                2 -> {
                    drawCircle(Color(0xFF67E8F9), r * 0.85f, center)
                    drawArc(
                        color = Color(0xFFF1F5F9),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - r * 0.85f, center.y - r * 0.2f),
                        size = Size(r * 1.7f, r * 1.1f)
                    )
                    drawCircle(Color.White, r * 0.2f, Offset(center.x - r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.Black, r * 0.08f, Offset(center.x - r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.White, r * 0.2f, Offset(center.x + r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.Black, r * 0.08f, Offset(center.x + r * 0.3f, center.y - r * 0.15f))
                }
                3 -> {
                    drawCircle(Color(0xFFF472B6), r * 0.9f, center)
                    drawCircle(Color(0xFFDB2777), r * 0.25f, Offset(center.x - r * 0.85f, center.y - r * 0.6f))
                    drawCircle(Color(0xFFDB2777), r * 0.25f, Offset(center.x + r * 0.85f, center.y - r * 0.6f))
                    drawCircle(Color(0xFF1E1B4B), r * 0.16f, Offset(center.x - r * 0.25f, center.y))
                    drawCircle(Color(0xFF1E1B4B), r * 0.16f, Offset(center.x + r * 0.25f, center.y))
                }
                else -> {
                    drawCircle(Color(0xFFFFD700), r * 0.95f, center)
                    drawCircle(Color(0xFFFFA500), r * 0.7f, center)
                    for (i in 0 until 5) {
                        val angle = (2 * PI * i) / 5
                        val sparkX = center.x + r * 0.75f * cos(angle).toFloat()
                        val sparkY = center.y + r * 0.75f * sin(angle).toFloat()
                        drawCircle(Color.White, r * 0.08f, Offset(sparkX, sparkY))
                    }
                    drawCircle(Color.White, r * 0.15f, Offset(center.x - r * 0.25f, center.y))
                    drawCircle(Color.White, r * 0.15f, Offset(center.x + r * 0.25f, center.y))
                }
            }
        }
    }
}

@Composable
fun SubjectTagSection(
    subjects: List<Subject>,
    selectedSubject: Subject?,
    onSelectSubject: (Subject) -> Unit,
    onAddSubjectClicked: () -> Unit,
    onDeleteSubject: (Subject) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TAG SUBJECT",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
            IconButton(
                onClick = onAddSubjectClicked,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Subject Tag",
                    tint = Color(0xFF818CF8)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (subjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No custom tags. Click + to begin tagging!",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(subjects) { subject ->
                    val isSelected = selectedSubject?.id == subject.id
                    val bubbleColor = Color(android.graphics.Color.parseColor(subject.colorHex))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) bubbleColor else Color(0x12FFFFFF))
                            .clickable { onSelectSubject(subject) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else bubbleColor)
                        )

                        Text(
                            text = subject.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.LightGray
                        )

                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete subject tag",
                            tint = if (isSelected) Color.White else Color.Gray,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteSubject(subject) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimerControls(
    uiState: TimerUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onStopwatchDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!uiState.isRunning) {
            Button(
                onClick = onStart,
                shape = CircleShape,
                modifier = Modifier
                    .width(160.dp)
                    .height(52.dp)
                    .testTag("start_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Session Runner",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "START FOCUS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        } else {
            if (uiState.isPaused) {
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume timer")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RESUME", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (uiState.timerType == "STOPWATCH") {
                            onStopwatchDone()
                        } else {
                            onReset()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = if (uiState.timerType == "STOPWATCH") Icons.Default.Save else Icons.Default.Refresh,
                        contentDescription = "Stop details"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.timerType == "STOPWATCH") "SAVE SESSION" else "RESET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onPause,
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563))
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause Session")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PAUSE FOCUS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (uiState.timerType == "STOPWATCH") {
                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onStopwatchDone,
                        modifier = Modifier
                            .height(48.dp)
                            .weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save stopwatch focus session")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE STUDY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentSessionsSection(
    sessions: List<StudySession>,
    onDeleteSession: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "RECENT STUDY SESSIONS",
            fontSize = 11.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0EFFFFFF))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A blank slate! Complete a study session above to earn companion XP.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sessions) { session ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotColor = Color(android.graphics.Color.parseColor(session.subjectColorHex))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.subjectName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${session.timerType} · ${session.durationSeconds / 60}m ${session.durationSeconds % 60}s",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Text(
                                text = "+${session.xpEarned} XP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF67E8F9)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete record entry",
                                    tint = Color(0xAAEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var subjectName by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#4F46E5") }
    val selectedIconName by remember { mutableStateOf("School") }

    val hexColors = listOf(
        "#4F46E5", // Indigo
        "#FF3366", // Rose Red
        "#00D084", // Pure Mint Green
        "#FB8C00", // Bright Sunset Orange
        "#00C0FF", // Sky Cyan
        "#CC33FF"  // Electric Purple
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CREATE NEW SUBJECT TAG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    placeholder = { Text("E.g., Quantum Physics", color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedContainerColor = Color(0x10FFFFFF),
                        unfocusedContainerColor = Color(0x10FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "TAG PALETTE",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    hexColors.forEach { hex ->
                        val isSelected = hex == selectedColorHex
                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .clickable { selectedColorHex = hex }
                                .padding(if (isSelected) 2.dp else 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (subjectName.isNotBlank()) {
                                onSave(subjectName, selectedColorHex, selectedIconName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Add Tag")
                    }
                }
            }
        }
    }
}
```
