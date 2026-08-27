package com.example.lyriccaptioner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiModel

@Composable
internal fun DeepSeekKeySettingsPanel(
    model: DeepSeekKeyUiModel,
    onSave: (String) -> Unit,
    onReplace: (String) -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit,
    onCancelInput: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    val hasExistingKey = model.maskedKey != null
    val showReplace = model.showReplace || (hasExistingKey && model.state == DeepSeekKeyState.VALIDATION_FAILED)
    val showDelete = model.showDelete || (hasExistingKey && model.state == DeepSeekKeyState.VALIDATION_FAILED)
    val showSave = model.showSave && !showReplace
    val operationInProgress = model.state == DeepSeekKeyState.VALIDATING_NEW_KEY ||
        model.state == DeepSeekKeyState.TESTING_CONNECTION
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171A1F),
            contentColor = Color(0xFFF4F5F7),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282D35)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AI 服务配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Provider: ${model.provider}", style = MaterialTheme.typography.bodySmall)
                    Text("状态：${deepSeekKeyStatusLabel(model)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9EA5B1))
                }
                TextButton(onClick = {
                    expanded = !expanded
                    if (!expanded) apiKeyInput = ""
                }) {
                    Text(if (expanded) "收起" else "配置")
                }
            }
            if (model.maskedKey != null) {
                Text(
                    text = "API Key：${model.maskedKey}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { contentDescription = "deepseek_key_masked" },
                )
            }
            if (model.detail != null) {
                Text(
                    text = model.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9EA5B1),
                    modifier = Modifier.semantics { contentDescription = "deepseek_key_detail" },
                )
            }
            if (expanded) {
                Text("Base URL：${model.baseUrl}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9EA5B1))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "deepseek_api_key_input" },
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showSave) {
                        Button(
                            modifier = Modifier.weight(1f).semantics { contentDescription = "deepseek_key_save" },
                            enabled = apiKeyInput.isNotBlank() && !operationInProgress,
                            onClick = {
                                val key = apiKeyInput
                                apiKeyInput = ""
                                onSave(key)
                            },
                        ) { Text("保存并验证") }
                    }
                    if (showReplace) {
                        Button(
                            modifier = Modifier.weight(1f).semantics { contentDescription = "deepseek_key_replace" },
                            enabled = apiKeyInput.isNotBlank() && !operationInProgress,
                            onClick = {
                                val key = apiKeyInput
                                apiKeyInput = ""
                                onReplace(key)
                            },
                        ) { Text("更换 API Key") }
                    }
                }
                if (model.showTestConnection) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "deepseek_key_test_connection" },
                        enabled = !operationInProgress,
                        onClick = onTestConnection,
                    ) { Text("测试连接") }
                }
                if (showDelete) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "deepseek_key_delete" },
                        onClick = {
                            apiKeyInput = ""
                            onDelete()
                        },
                    ) { Text("删除 API Key") }
                }
                if (model.showCancel) {
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "deepseek_key_cancel" },
                        onClick = {
                            apiKeyInput = ""
                            onCancelInput()
                        },
                    ) { Text("取消") }
                }
            }
        }
    }
}

internal fun deepSeekKeyStatusLabel(model: DeepSeekKeyUiModel): String = when (model.state) {
    DeepSeekKeyState.UNCONFIGURED -> "未配置"
    DeepSeekKeyState.INPUT_NEW_KEY -> "请输入新 Key"
    DeepSeekKeyState.VALIDATING_NEW_KEY -> "验证中…"
    DeepSeekKeyState.TESTING_CONNECTION -> "正在测试连接…"
    DeepSeekKeyState.CONFIGURED -> "已配置"
    DeepSeekKeyState.VALIDATION_FAILED -> "验证失败，旧 Key 保持不变"
    DeepSeekKeyState.NEEDS_REENTRY -> "需要重新输入"
}
