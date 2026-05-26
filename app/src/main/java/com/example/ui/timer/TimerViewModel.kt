package com.example.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.Subject
import com.example.data.database.StudySession
import com.example.data.database.UserProfile
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TimerUiState(
    val timerType: String = "POMODORO", // "POMODORO", "STOPWATCH", "CUSTOM"
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val totalDurationSeconds: Long = 1500, // Default 25 min
    val timeLeftSeconds: Long = 1500,
    val selectedSubject: Subject? = null,
    val currentPhase: String = "WORK" // "WORK", "BREAK"
)

class TimerViewModel(private val repository: StudyRepository) : ViewModel() {

    private val _timerState = MutableStateFlow(TimerUiState())
    val timerState: StateFlow<TimerUiState> = _timerState.asStateFlow()

    // Database Flows
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
    private var actualSessionStartTimestamp: Long = 0L

    init {
        viewModelScope.launch {
            repository.populateDefaultsIfEmpty()
            // Auto-select first subject if available
            subjects.first { it.isNotEmpty() }.let { list ->
                if (list.isNotEmpty() && _timerState.value.selectedSubject == null) {
                    _timerState.update { it.copy(selectedSubject = list.first()) }
                }
            }
        }
    }

    fun selectSubject(subject: Subject) {
        _timerState.update { it.copy(selectedSubject = subject) }
    }

    fun setTimerType(type: String) {
        if (_timerState.value.isRunning) return // Can't change type while running

        val duration = when (type) {
            "POMODORO" -> 1500L // 25 Min
            "STOPWATCH" -> 0L
            "CUSTOM" -> 600L // Default 10 Min custom
            else -> 1500L
        }
        _timerState.update {
            it.copy(
                timerType = type,
                totalDurationSeconds = duration,
                timeLeftSeconds = duration,
                isRunning = false,
                isPaused = false,
                currentPhase = "WORK"
            )
        }
    }

    fun setCustomDurationMinutes(minutes: Int) {
        if (_timerState.value.timerType != "CUSTOM" || _timerState.value.isRunning) return
        val secs = minutes * 60L
        _timerState.update {
            it.copy(
                totalDurationSeconds = secs,
                timeLeftSeconds = secs
            )
        }
    }

    fun startTimer() {
        if (_timerState.value.isRunning && !_timerState.value.isPaused) return

        actualSessionStartTimestamp = System.currentTimeMillis()
        timerJob?.cancel()

        _timerState.update { it.copy(isRunning = true, isPaused = false) }

        timerJob = viewModelScope.launch {
            if (_timerState.value.timerType == "STOPWATCH") {
                // Stopwatch flows UP
                while (true) {
                    delay(1000)
                    _timerState.update {
                        it.copy(
                            timeLeftSeconds = it.timeLeftSeconds + 1,
                            totalDurationSeconds = it.totalDurationSeconds + 1
                        )
                    }
                }
            } else {
                // Countdown styles: Pomodoro or Custom
                while (_timerState.value.timeLeftSeconds > 0) {
                    delay(1000)
                    _timerState.update { it.copy(timeLeftSeconds = it.timeLeftSeconds - 1) }
                }
                // When countdown hits 0:
                onTimerCompleted()
            }
        }
    }

    fun pauseTimer() {
        if (!_timerState.value.isRunning || _timerState.value.isPaused) return
        timerJob?.cancel()
        _timerState.update { it.copy(isPaused = true) }
    }

    fun resumeTimer() {
        if (!_timerState.value.isRunning || !_timerState.value.isPaused) return
        _timerState.update { it.copy(isPaused = false) }
        startTimer()
    }

    fun finishAndSaveStopwatch() {
        if (_timerState.value.timerType != "STOPWATCH") return
        val currentDuration = _timerState.value.totalDurationSeconds
        timerJob?.cancel()

        viewModelScope.launch {
            val selectedSubj = _timerState.value.selectedSubject ?: return@launch
            if (currentDuration >= 1) { // Save session if they focused at least 1 second
                repository.saveStudySession(
                    subjectId = selectedSubj.id,
                    subjectName = selectedSubj.name,
                    subjectColorHex = selectedSubj.colorHex,
                    durationSeconds = currentDuration,
                    timerType = "STOPWATCH"
                )
            }
            // Reset stopwatch
            _timerState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    timeLeftSeconds = 0,
                    totalDurationSeconds = 0
                )
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        val defaultDuration = when (_timerState.value.timerType) {
            "POMODORO" -> if (_timerState.value.currentPhase == "WORK") 1500L else 300L
            "STOPWATCH" -> 0L
            "CUSTOM" -> _timerState.value.totalDurationSeconds
            else -> 1500L
        }
        _timerState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                timeLeftSeconds = defaultDuration,
                totalDurationSeconds = if (_timerState.value.timerType == "STOPWATCH") 0L else it.totalDurationSeconds
            )
        }
    }

    private suspend fun onTimerCompleted() {
        timerJob?.cancel()
        val currentType = _timerState.value.timerType
        val duration = _timerState.value.totalDurationSeconds
        val subject = _timerState.value.selectedSubject
        val phase = _timerState.value.currentPhase

        if (currentType == "POMODORO" && phase == "WORK" && subject != null) {
            // Completed WORK session! Save session details to Room DB.
            repository.saveStudySession(
                subjectId = subject.id,
                subjectName = subject.name,
                subjectColorHex = subject.colorHex,
                durationSeconds = duration,
                timerType = "POMODORO"
            )
            // Enter break phase
            _timerState.update {
                it.copy(
                    currentPhase = "BREAK",
                    timeLeftSeconds = 300L, // 5 minute resting break
                    totalDurationSeconds = 300L,
                    isRunning = false,
                    isPaused = false
                )
            }
        } else if (currentType == "POMODORO" && phase == "BREAK") {
            // Break completed! Push back to work phase.
            _timerState.update {
                it.copy(
                    currentPhase = "WORK",
                    timeLeftSeconds = 1500L, // 25 min back to focus
                    totalDurationSeconds = 1500L,
                    isRunning = false,
                    isPaused = false
                )
            }
        } else if (currentType == "CUSTOM" && subject != null) {
            repository.saveStudySession(
                subjectId = subject.id,
                subjectName = subject.name,
                subjectColorHex = subject.colorHex,
                durationSeconds = duration,
                timerType = "CUSTOM"
            )
            _timerState.update {
                it.copy(
                    isRunning = false,
                    isPaused = false,
                    timeLeftSeconds = duration
                )
            }
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
            // If deleted subject was selected, update selection to another subject
            if (_timerState.value.selectedSubject?.id == subject.id) {
                val rem = subjects.value.filter { it.id != subject.id }
                _timerState.update { it.copy(selectedSubject = rem.firstOrNull()) }
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
