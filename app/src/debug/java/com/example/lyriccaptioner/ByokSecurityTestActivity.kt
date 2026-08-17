package com.example.lyriccaptioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiMapper
import com.example.lyriccaptioner.ui.DeepSeekKeySettingsPanel
import com.example.lyriccaptioner.ui.LyricCaptionerTheme
import java.util.concurrent.atomic.AtomicBoolean

class ByokSecurityTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cancelInvoked.set(false)
        saveInvoked.set(false)
        replaceInvoked.set(false)
        testConnectionInvoked.set(false)
        deleteInvoked.set(false)
        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()
        val status = when (mode) {
            MODE_CONFIGURED -> DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "••••••••3456")
            MODE_UNCONFIGURED -> DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
            else -> DeepSeekKeyStatus(DeepSeekKeyState.VALIDATING_NEW_KEY, "••••••••3456")
        }
        setContent {
            LyricCaptionerTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    DeepSeekKeySettingsPanel(
                        model = DeepSeekKeyUiMapper.from(status),
                        onSave = { saveInvoked.set(true) },
                        onReplace = { replaceInvoked.set(true) },
                        onTestConnection = { testConnectionInvoked.set(true) },
                        onDelete = { deleteInvoked.set(true) },
                        onCancelInput = { cancelInvoked.set(true) },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_CONFIGURED = "configured"
        const val MODE_UNCONFIGURED = "unconfigured"
        val cancelInvoked = AtomicBoolean(false)
        val saveInvoked = AtomicBoolean(false)
        val replaceInvoked = AtomicBoolean(false)
        val testConnectionInvoked = AtomicBoolean(false)
        val deleteInvoked = AtomicBoolean(false)
    }
}
