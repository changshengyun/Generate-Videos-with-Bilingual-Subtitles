# Current Task: V3-AI-CONTRACT-001

## Current status

- Stage: `V3-AI-CONTRACT-001`
- Status: `PARTIAL_PASS / SECURE_BYOK_COMPONENT_VERIFIED / LIVE_KEY_TEST_REQUIRED`（Developer 候选，待 Brain 裁决）
- Previous stage: `V3-DEC-001 / PASS`
- Scope: R1 安全本地 Key 存储、真实取消、非请求路径无明文解密、串行保存/替换/取消/删除、最小配置入口与 Android Keystore 运行时证明
- V2 functional baseline: `8a48d88`
- Documentation baseline: `3117eb1`
- Implementation authorization: `APPROVED_BY_USER`
- Live API status: `DEFERRED_UNTIL_PROVIDER_AND_KEY_CONFIGURATION`
- Review workflow: 不启用独立 Review 窗口；Developer 完成自测并把矩阵证据交回 Brain 判定
- Next action: Brain 按冻结的 R1-R01 至 R1-R10 证据重新裁决；不得在裁决前启动下一模块或声称真实 Key/云端认证通过

## V3-AI-CONTRACT-001-R1 acceptance matrix

| Category | R1 requirement |
|---|---|
| Main path | App AI service settings -> masked DeepSeek BYOK input -> minimal validation -> Android Keystore AES-256-GCM encryption -> `noBackupFilesDir` ciphertext/IV record -> short-lived decrypt only for request construction. |
| Mandatory evidence | R1-R01 至 R1-R10；真实取消与 write count；status/cancel decrypt count 为 0；统一串行化与可见删除失败；production `AndroidKeystoreDeepSeekKeyStore` round-trip/corruption/alias-loss/delete instrumentation；masked settings UI；focused/full JVM、lint、普通 Debug、native-enabled Debug、AndroidTest 构建与 secret scan。 |
| Prohibited | Real key, backend/provider expansion, online lyrics, UI redesign, Whisper/cache/media/editor/SRT cleanup, plaintext key in preferences/DataStore/archive/SavedState/logs/APK/tests. |
| Exit | R1-R01 至 R1-R10、production Android Keystore instrumentation、完整构建矩阵、secret scan 和三份文档全部通过后，仅允许回交候选状态 `PARTIAL_PASS / SECURE_BYOK_COMPONENT_VERIFIED / LIVE_KEY_TEST_REQUIRED`；真实 Key 产品流仍需后续授权。 |
| Incomplete | JVM/构建通过但 Android Keystore instrumentation 未运行：`PARTIAL_PASS / ANDROID_KEYSTORE_RUNTIME_TEST_REQUIRED / LIVE_KEY_TEST_REQUIRED`；安全、取消、原子删除或明文生命周期无法证明：`BLOCKED / SECURITY_PROOF_REQUIRED`。 |

## R1 security/BYOK rework evidence (2026-08-10)

- Checkpoint: `bbb9761`；旧实现红线基线 6 项中 R1-R03、R1-R04、R1-R05 共 3 项失败，分别证明删除后写回、删除吞错和 status/cancel 明文解密缺口。
- R1 focused JVM：24 tests passed；完整 `:app:testDebugUnitTest`：145 tests、0 failures、0 skipped；`python tools\asr_evaluate_test.py`：6 tests passed。
- `:app:lintDebug`、`:app:assembleDebug`、`-PenableWhisperNative=true :app:assembleDebug`、`:app:assembleDebugAndroidTest` 全部通过；native build 的 NDK strip permission warning 未阻止构建。
- production `AndroidKeystoreDeepSeekKeyStore` instrumentation 在既有 `Pixel_8 / emulator-5554 / sdk_gphone64_x86_64 / API 36` 通过：AndroidKeyStore AES-256-GCM、12-byte IV、106-byte test-owned `noBackupFilesDir` record、重启恢复、IV 轮换、ciphertext corruption、alias loss、re-entry、可见且脱敏的删除失败与最终 delete 均通过。
- ViewModel 取消证据：validation Job 已 cancel-and-join，probe 已进入后被取消，释放 probe 后 write count 仍为 0，最终为 `UNCONFIGURED`。
- `status()` 与 `cancelInput()` 只调用不返回明文的 health 接口；JVM decrypt count 为 0；只有 `withDecryptedKey` 调用返回完整 Key 的 `decrypt()`，对应 decrypt count 为 1。
- Compose instrumentation：密码 semantics、仅末四位掩码、非空掩码截图、验证中真实取消入口，以及 save/replace/collapse/cancel/delete 后输入清空均通过；UI semantics 未出现完整 synthetic sentinel。
- source/resources/test-output/APK secret scan 未发现真实 Key、实际 Authorization credential、歌词正文或私有运行路径；AndroidTest 只使用 synthetic sentinel 与测试专用 alias/record。
- 本状态仅是 Developer 候选；没有真实 DeepSeek Key、真实 Provider 网络调用、真实认证、歌词匹配或完整设备产品流。

## Historical R1 implementation evidence before security rework (2026-08-10)

- R1 checkpoint: `a18574`; implementation commits: `6e77550`, `d9addce`; no push.
- Agent A: strict fallback allowlist and sanitized provider/validation/programming/cancellation failures in `CaptionEnhancementCoordinator.kt`.
- Agent B: `DeepSeekByokManagerImpl` plus `AndroidKeystoreDeepSeekKeyStore`; AES-256-GCM, Android Keystore alias, random 12-byte IV, atomic private `noBackupFilesDir` record, replacement/delete/corruption/concurrency handling.
- Agent C: injectable manager in `MainViewModel.kt` and a collapsible AI service configuration panel in `EditorScreen.kt`; password input is transient and cleared after actions/collapse; only masked suffix is exposed.
- R1 focused security/BYOK/UI tests: 16 passed. Full `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, native-equivalent `-PenableWhisperNative=true :app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed.
- `:app:assembleNativeDebug` remains absent in this checkout; native-enabled Debug exercised the configured CMake route. Kotlin daemon/NDK strip permission warnings remain environmental.
- No real DeepSeek key, live probe, network call, device product-flow verification, provider lyrics retrieval, backup restore test, or APK/runtime secret scan with a real key was performed. Brain must keep `LIVE_KEY_TEST_REQUIRED`.

## Orchestrator implementation evidence (2026-08-10)

- Checkpoint commit: `bfc7751` (`test(v3): freeze caption enhancement contract`).
- Feature commit: `69b991e` (`feat(v3): implement caption enhancement contract`). No push performed.
- Agent A owns request mapping and response validation; Agent B owns coordinator/error mapping/local fallback; Agent C owns processing snapshot, atomic commit policy, editor/project state and V3 archive compatibility.
- Focused four-test command: PASS. Full `:app:testDebugUnitTest`: PASS. `:app:lintDebug`: PASS. `:app:assembleDebug`: PASS. Native-equivalent `-PenableWhisperNative=true :app:assembleDebug`: PASS. `:app:assembleDebugAndroidTest`: PASS.
- The requested `:app:assembleNativeDebug` task does not exist in this checkout; the native-enabled Debug command above exercised the configured CMake native path. Kotlin daemon and NDK strip permission warnings were environmental; fallback compilation/build completed successfully.
- No live Provider, API key, network lyrics retrieval, device run, UI/media change, model/cache change, or V2 cleanup was performed. Brain must adjudicate the stage; this is not a formal product acceptance claim.

## Confirmed product decisions

1. 本地 Whisper 先生成带 `cue_id/start_ms/end_ms/raw_english` 的分段字幕；后端不重新识别音频。
2. 完整原始字幕批次通过 API 交给 AI。AI 根据现有识别文本匹配对应歌曲和在线歌词，再按原 cue 修正英文并返回中文翻译。
3. 云端响应不得改变 cue ID 或时间区间，不得静默增删、合并、拆分或重排字幕。
4. 云端不可用、超时、服务错误或响应校验失败时，保留 Whisper 原始英文，并使用现有本地 OPUS-MT 生成中文；结果必须标记为 `LOCAL_FALLBACK`。
5. Whisper 使用单模型进程级缓存：识别结束后保留 context 3-5 分钟；空闲超时、模型切换、严重内存压力或取消后状态不安全时释放；同一 context 串行使用。
6. 字幕文本框统一调整宽度、水平位置和垂直位置；高度根据中英文、字号、行距和内边距自动计算，并动态限制最小宽度/安全边界，禁止文字裁切或覆盖。
7. 每段字幕可独立覆盖字体、字号、中英文字色、描边色、粗体、斜体和对齐；V2 样式迁移为项目默认样式，新增设置只保存差异覆盖。
8. 导入只使用系统相册/Photo Picker；导出只保存到系统相册/MediaStore，不再提供自定义位置。
9. 删除 App 顶栏，但保留系统状态栏、导航栏和 Window Insets。
10. 识别成功后不自动跳转，只显示“识别成功”；用户自行点击现有“编辑字幕”入口。
11. 当前密钥路线为 `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED`：供应商 API Key 只允许由用户在设备内输入，以 Android Keystore 包装的 AES-256-GCM 密文保存；不得写入 APK、Git、文档、日志、普通 Preferences/DataStore 或项目归档。真实 Provider 认证与网络调用延后。
12. V3 最终只保留两条产品处理链路：主链路“视频导入 -> 模型识别 -> 云端匹配/修正/翻译 -> 字幕编辑确认 -> 导出”和降级链路“云端不可用 -> 本地 OPUS-MT -> 编辑确认 -> 导出”。SRT 插入及其他替代导出分支在独立 `V3-CLEAN-001` 清单和回归门禁后删除。

## Stage state machine

```text
V3-DEC-001 / PASS
  -> V3-AI-CONTRACT-001 / MATRIX_DEFINED / IN_PROGRESS
  -> BRAIN_TEST_SPEC_FROZEN
  -> TEST_FILES_ADDED
  -> RED_BASELINE_CAPTURED
  -> CHECKPOINT_CREATED
  -> PARALLEL_IMPLEMENTING
  -> SERIAL_INTEGRATION
  -> FOCUSED_TESTS_PASSED
  -> FULL_STAGE_MATRIX_PASSED
  -> READY_FOR_BRAIN
       -> PASS / COMPONENT_VERIFIED / LIVE_API_DEFERRED
       -> PARTIAL_PASS
       -> BLOCKED
       -> HUMAN_DECISION
```

## Brain test-first baseline

- Added 20 deterministic JVM contract tests across four new test files; no production Kotlin was added or modified.
- Focused command: `./gradlew.bat :app:testDebugUnitTest --tests "com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementContractTest" --tests "com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementCoordinatorTest" --tests "com.example.lyriccaptioner.model.CaptionBatchCommitPolicyTest" --tests "com.example.lyriccaptioner.project.ProjectArchiveV3ContractTest"`.
- Expected-red result: `:app:compileDebugUnitTestKotlin FAILED` because the new production contract, validator, coordinator, processing snapshot and commit policy do not yet exist. The compiler reported `unresolved reference` for those frozen symbols; this is the intended test-first boundary.
- Environment note: the Kotlin daemon also hit `AccessDeniedException` under the user-local daemon directory, then Gradle used its fallback compiler and emitted the expected missing-production-symbol errors. This does not convert the red baseline into an implementation defect or PASS.
- Test privacy wording excludes the necessary network wire payload and user-saved project archive; it applies to diagnostics, logs, exceptions, debug strings, telemetry and automatic snapshots.

没有独立 Review Agent/Review 窗口。Developer 不自称正式验收，只提交测试与证据；Brain 按验收矩阵决定最终状态和下一阶段。

## 历史合同实现证据（非当前 Next action）

以下原始 T01-T14 合同矩阵与实现结果作为历史证据保留，不再充当当前调度入口；当前执行面是上方 R1 安全增量矩阵。

## Historical acceptance matrix

| 类别 | `V3-AI-CONTRACT-001` 固定内容 |
|---|---|
| 主链路 | 固定的 Whisper cue 批次进入 `CaptionEnhancementService` -> 生成严格请求 -> Provider 返回歌曲匹配与逐 cue 英文修正/中文翻译 -> 本地完整校验 -> 整批原子提交；Provider 不可用或响应无效时，用原始英文调用 `LocalTranslator` -> 整批本地中文结果提交并标记来源 |
| 必须证据 | 测试先于生产实现；下方 T01-T14 全部通过；新增/修改行为有单元或契约测试；相关 JVM 测试、`testDebugUnitTest`、`lintDebug`、普通 Debug、Native Debug 与 AndroidTest 构建通过；报告测试数量、命令结果和关键状态机日志；证明无真实 API Key、完整歌词或私有媒体路径泄露 |
| 禁止事项 | 不接入真实 AI Provider、不创建或选择后端技术栈、不要求用户提供 API Key、不抓取真实在线歌词、不修改 UI、Whisper native/cache、Media3、FFmpegKit、字幕坐标、系统相册流程或 V2 清理范围；不删除 SRT/旧分支；不使用 Demo/fixed lyrics 冒充云端成功 |
| 退出状态 | 所有合同、校验、原子提交、云端失败映射、本地回退、取消和日志隐私测试通过，完整构建矩阵通过，且 Git 只包含本阶段合同/测试/三份活动文档和已授权治理变更时，允许 `PASS / COMPONENT_VERIFIED / LIVE_API_DEFERRED` |
| 未完成状态 | 合同实现和测试完成但完整构建未通过：`PARTIAL_PASS / BUILD_REQUIRED`；核心状态机或原子性测试失败：`BLOCKED`；必须选择真实 Provider、后端栈、歌词来源授权或密钥方案才能继续：`HUMAN_DECISION`；没有真实 Provider/Key 只保持 `LIVE_API_DEFERRED`，不阻止本合同阶段 PASS |

## Test cases to write before production code

| ID | 测试内容 | 必须断言 |
|---|---|---|
| `T01` | 请求映射 | `job_id/schema_version` 合法，cue ID、顺序、时间区间和原始英文完整保留 |
| `T02` | 云端成功 | 每个请求 cue 恰好获得一条修正英文和中文，来源为 `CLOUD_AI`，整批一次提交 |
| `T03` | 歌曲匹配信息 | 歌名、歌手、匹配置信度和来源可选且受长度/枚举约束；无可靠匹配时不得伪造已确认歌曲 |
| `T04` | cue 集合异常 | 缺失、额外或重复 cue ID 时拒绝整批响应，不部分覆盖 |
| `T05` | 时间轴被修改 | 任一 `start_ms/end_ms` 与请求不同即拒绝整批响应 |
| `T06` | 字段与大小限制 | 空英文、非法 Unicode/枚举、超长文本、错误 schema/job ID 被拒绝 |
| `T07` | 云端不可用 | 离线、连接失败、超时、可重试 5xx 和无效 Schema 进入本地翻译回退 |
| `T08` | 本地回退输入 | OPUS-MT 使用原始 Whisper 英文；输出来源为 `LOCAL_FALLBACK`，不声称英文已被 AI 修正 |
| `T09` | 云端成功不回退 | 有效云端结果提交后不得再调用本地 translator |
| `T10` | 用户取消 | 取消进入 `CANCELLED`，不自动启动本地回退，不覆盖当前字幕 |
| `T11` | 本地回退失败 | 保留完整原始英文和既有项目状态，不提交半批中文，返回可恢复错误 |
| `T12` | 原子性 | 云端或本地任一 cue 失败时，整批不提交；旧导出失效只在完整新字幕提交后触发 |
| `T13` | 来源与状态持久化 | `CLOUD_AI/LOCAL_FALLBACK/RAW_ASR`、处理版本和错误状态能保存恢复，不混淆来源 |
| `T14` | 隐私与密钥 | 诊断日志、异常、debug string、遥测事件和自动测试快照不包含 API Key、完整歌词批次或用户私有媒体路径；实际网络 wire payload 必须携带原始英文、用户主动保存的项目归档允许持久化字幕，二者不属于该日志隐私断言；仅允许非敏感 API 模式配置持久化 |

## Authorized implementation scope

- 新增或调整 Provider-neutral 的请求/响应 DTO、`CaptionEnhancementService` 接口、响应校验器、结果来源枚举、状态机和错误映射。
- 使用 fake Provider 与 fake/local translator 编写确定性测试。
- 在不接真实网络的前提下实现云端成功与本地回退的 orchestration 边界。
- 必要时对 `CaptionCue`/项目状态增加最小兼容字段，但不得在本阶段重做 UI 或迁移全部历史项目结构。
- 先增加测试和失败基线，再实现生产代码；普通失败按 `AGENTS.md` 自主 Debug。

## API key boundary

- 当前已确认路线为 `DEVICE_DIRECT_BYOK / ANDROID_KEYSTORE_REQUIRED`。
- R1 只实现用户输入、Android Keystore 包装的 AES-256-GCM 本地密文、最小配置 UI 与短生命周期解密边界；不接入真实 Provider 网络调用。
- 真实认证、歌词匹配、完整云端链路与真实用户 Key 测试留给后续明确授权阶段。

## Final report format

1. 候选状态及证据等级，不自称正式验收；
2. 验收矩阵五项逐条结果；
3. T01-T14 及完整构建矩阵的实际命令、数量和结果；
4. 实现文件、测试文件和明确未修改范围；
5. 云端成功、本地回退、取消、原子失败的状态转换证据；
6. API Key/歌词/路径未泄露检查；
7. checkpoint、功能 Commit、是否 push 和最终 Git 状态；
8. `LIVE_API_DEFERRED` 的剩余条件，以及下一阶段建议。
