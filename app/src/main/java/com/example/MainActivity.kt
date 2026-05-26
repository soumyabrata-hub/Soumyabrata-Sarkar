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

