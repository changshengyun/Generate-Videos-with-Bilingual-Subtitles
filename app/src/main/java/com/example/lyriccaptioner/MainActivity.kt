package com.example.lyriccaptioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.lyriccaptioner.ui.EditorScreen
import com.example.lyriccaptioner.ui.LyricCaptionerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LyricCaptionerTheme {
                EditorScreen(viewModel = viewModel)
            }
        }
    }
}
