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

    // Cyber Zen Gradient Theme Background
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
            // 1. User XP & Streak Dashboard
            UserProfileHeader(userProfile = userProfile)

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Mode Segment Picker
            TimerTypeSelector(
                currentType = uiState.timerType,
                isRunning = uiState.isRunning,
                onSelectType = { viewModel.setTimerType(it) }
            )

            // 3. Circular Indicator & Evolution Companion
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

            // 4. Subject Tags List
            SubjectTagSection(
                subjects = subjects,
                selectedSubject = uiState.selectedSubject,
                onSelectSubject = { viewModel.selectSubject(it) },
                onAddSubjectClicked = { showAddSubjectDialog = true },
                onDeleteSubject = { viewModel.deleteSubject(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Playback Media Controls
            TimerControls(
                uiState = uiState,
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onResume = { viewModel.resumeTimer() },
                onReset = { viewModel.resetTimer() },
                onStopwatchDone = { viewModel.finishAndSaveStopwatch() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Recent Session Logs
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
            // Level Badge Circle
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

            // Companion details & Progress bar
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

            // Flame Streak Box
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

    // Interactive pulser animation
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

            // Backdrop ring
            drawCircle(
                color = Color(0x10FFFFFF),
                radius = diameter / 2,
                style = Stroke(width = strokeWidth)
            )

            // Neon Foreground Loader indicator Arc
            if (uiState.timerType != "STOPWATCH") {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF22D55E), // Green
                            Color(0xFF38BDF8), // Cyan Glow
                            Color(0xFF818CF8), // Indigo Bloom
                            Color(0xFFF43F5E), // Pink Rose
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

        // Inner stats column inside countdown ring
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
                    // Sleepy Core Egg
                    drawOval(
                        color = Color(0xFFFEF08A),
                        topLeft = Offset(center.x - r * 0.7f, center.y - r * 0.9f),
                        size = Size(r * 1.4f, r * 1.8f)
                    )
                    // Egg spots
                    drawCircle(Color(0xFFFACC15), r * 0.15f, Offset(center.x - r * 0.3f, center.y - r * 0.1f))
                    // Sleep slits
                    val eyeY = center.y + r * 0.1f
                    drawLine(Color(0xFF713F12), Offset(center.x - r * 0.45f, eyeY), Offset(center.x - r * 0.2f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF713F12), Offset(center.x + r * 0.2f, eyeY), Offset(center.x + r * 0.45f, eyeY), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                2 -> {
                    // Cute Baby Hatchling
                    drawCircle(Color(0xFF67E8F9), r * 0.85f, center)
                    // Eggshell cup
                    drawArc(
                        color = Color(0xFFF1F5F9),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - r * 0.85f, center.y - r * 0.2f),
                        size = Size(r * 1.7f, r * 1.1f)
                    )
                    // Cute googly eyes
                    drawCircle(Color.White, r * 0.2f, Offset(center.x - r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.Black, r * 0.08f, Offset(center.x - r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.White, r * 0.2f, Offset(center.x + r * 0.3f, center.y - r * 0.15f))
                    drawCircle(Color.Black, r * 0.08f, Offset(center.x + r * 0.3f, center.y - r * 0.15f))
                }
                3 -> {
                    // Teen Guardian
                    drawCircle(Color(0xFFF472B6), r * 0.9f, center)
                    // Guardian Cheek indicators
                    drawCircle(Color(0xFFDB2777), r * 0.25f, Offset(center.x - r * 0.85f, center.y - r * 0.6f))
                    drawCircle(Color(0xFFDB2777), r * 0.25f, Offset(center.x + r * 0.85f, center.y - r * 0.6f))
                    // Dark cute eyes
                    drawCircle(Color(0xFF1E1B4B), r * 0.16f, Offset(center.x - r * 0.25f, center.y))
                    drawCircle(Color(0xFF1E1B4B), r * 0.16f, Offset(center.x + r * 0.25f, center.y))
                }
                else -> {
                    // Celestial Zenith Master
                    drawCircle(Color(0xFFFFD700), r * 0.95f, center)
                    drawCircle(Color(0xFFFFA500), r * 0.7f, center)
                    // Star particles
                    for (i in 0 until 5) {
                        val angle = (2 * PI * i) / 5
                        val sparkX = center.x + r * 0.75f * cos(angle).toFloat()
                        val sparkY = center.y + r * 0.75f * sin(angle).toFloat()
                        drawCircle(Color.White, r * 0.08f, Offset(sparkX, sparkY))
                    }
                    // Celestial shining eyes
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
