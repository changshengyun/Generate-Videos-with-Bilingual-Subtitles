# Bug 登记簿

按时间倒序登记已定位并修复（或确认）的缺陷。每条包含：现象、根因、修复、验证证据。

---

## BUG-2026-0827-001 分段 AI 增强点击无反应（假性功能失效）

- 等级：高（用户可感知功能"消失"）
- 发现时间：2026-08-27
- 状态：已修复并真机验证（提交见文末）

### 现象

字幕编辑页点击单条字幕的「AI 增强」按钮后界面完全无反应：无加载态、无错误提示、日志无异常。用户误判为功能被删除。同页「拆分字幕」「合并字幕」等功能实测正常。

### 根因（两因叠加）

1. **触发因**：设备上保存的 DeepSeek Key 为 `••••••••4bc2`，该 Key 已被 DeepSeek 判定失效（返回 401 `Authentication Fails`）。点击「AI 增强」后请求真实发出并失败，属预期失败路径。
2. **缺陷因**：`ui/CaptionEditorPage.kt` 第 188 行（提交 `3e149d3` 引入）错误提示的显示条件写成
   `cueSuggestion.error.takeIf { cueSuggestion.running && cueSuggestion.cueId == cue.id }`。
   而 `MainViewModel.requestCueSuggestion` 的所有出错路径（Key 未配置 / 请求失败 / 字幕已变化）设置 `error` 时 `running` 恒为 `false`，导致错误文案**永远不显示**，失败被静默吞没。

### 排查证据

- 真机复现：`adb input tap` 模拟点击，`uiautomator dump` 确认按钮存在、点击事件已送达（`MIUIInput MotionEvent`），界面无任何变化。
- 拆分功能对照实验：点击「拆分字幕」后列表由 4 条变 5 条，证明编辑链路正常。
- Key 状态：应用首页显示「状态：已配置，API Key：••••••••4bc2」；`.env` 同尾缀 Key 经 Live 测试确认 401。
- 日志：`logcat` 全缓冲无 `FATAL`/`AndroidRuntime`/异常堆栈。

### 修复

`CaptionEditorPage.kt`：去掉 `running` 门控，错误提示只要归属当前字幕条目即显示：

```kotlin
aiError = cueSuggestion.error.takeIf { cueSuggestion.cueId == cue.id },
```

### 验证

- `compileDebugKotlin` 通过；全量 `testDebugUnitTest` 绿。
- 修复版 APK 已装机（设备 `fcf4b0cb`）。
- 2026-08-27 19:47 真机端到端复验：用户替换有效 Key 后，整流程状态显示 `DeepSeek enhanced 4 captions.`；点击单条「AI 增强」正常弹出「AI 增强建议」对话框（当前内容 / AI 建议 / 应用按钮），功能闭环。

### 遗留事项

- 用户需在「AI 服务配置」替换为有效的 DeepSeek Key，AI 增强才能真正返回结果；替换前点击会看到明确的错误提示而非静默。
