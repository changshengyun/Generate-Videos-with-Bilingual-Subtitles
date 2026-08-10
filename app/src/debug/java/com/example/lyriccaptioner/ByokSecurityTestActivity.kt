package com.example.lyriccaptioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent {
            LyricCaptionerTheme {
                DeepSeekKeySettingsPanel(
                    model = DeepSeekKeyUiMapper.from(
                        DeepSeekKeyStatus(
                            state = DeepSeekKeyState.VALIDATING_NEW_KEY,
                            maskedKey = "••••••••3456",
                        ),
                    ),
                    onSave = {},
                    onReplace = {},
                    onDelete = {},
                    onCancelInput = { cancelInvoked.set(true) },
                )
            }
        }
    }

    companion object {
        val cancelInvoked = AtomicBoolean(false)
    }
}
