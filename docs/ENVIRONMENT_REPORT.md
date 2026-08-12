# V3-AI-001 检索增强环境报告

- Windows / PowerShell 工作区：`D:\DevEnv\Projects\lyric-captioner-android`
- Android：AGP 8.7.3、Kotlin 2.0.21、compileSdk 36、minSdk 26、targetSdk 35。
- App 已具备 `INTERNET` 权限和基于 `HttpURLConnection` 的 DeepSeek HTTPS Provider。
- 目标真机基线：物理 ARM64、API 36；本修订先完成组件与构建验证。
- 歌词检索候选：LRCLIB 只读 `/api/search`，无需新 API Key；客户端必须提供标识头并串行请求。
- 不新增依赖；沿用 Kotlin/JDK 网络与现有手写 JSON 边界。
- 全局环境变更：无。回滚为删除新增 SearchTool 接入并恢复 Provider v1 路由。
