# 沙箱三视频测试报告（新流程版 + 搜索诊断）

运行时间：2026-08-29T17:56:13.173114
端点：Responses API (`https://api.deepseek.com/responses`)
模型：`deepseek-v4-pro`

## 整体流程
```
输入字幕 → 流程1+2（AI带官方联网搜索找歌取词）
         → 本地DP比对（唯一确认闸门）
         → 三区间分流：
             CONFIRMED（≥0.82/0.76）→ 确认歌曲
             MIDDLE_ZONE（50%~门槛）→ 逐条修复+保守翻译
             RESEARCH（<50%）→ 带失败反馈重搜（上限5轮）
         → 流程3（双语生成）→ 流程4（合同校验）→ 落屏
```

## 视频1（9条字幕）

### 基本信息
| 字段 | 值 |
|---|---|
| 字幕条数 | 9 |
| 处理版本 | sandbox-web-search-v1 |
| 歌曲状态 | NOT_FOUND |
| 歌曲 | N/A - N/A |
| 置信度 | N/A |

### 搜索诊断
| 轮次 | 识别歌曲 | 匹配率 | 分类 | 搜索次数 |
|---|---|---|---|---|
| R1 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R2 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R3 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R4 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R5 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R6 | (解析失败) | N/A | PARSE_FAILED | 11 |

### 输出字幕
- **v1-0**: Creeping up on you / 悄悄靠近你
- **v1-1**: I know that it won't be long / 我知道不会太久
- **v1-2**: I guess I like it this way / 我想我喜欢这样
- **v1-3**: And I don't wanna fight / 我不想争吵
- **v1-4**: I'm not the kind of guy / 我不是那种人
- **v1-5**: Who's gonna hurt you / 会伤害你的人
- **v1-6**: Or leave you behind / 或弃你而去
- **v1-7**: I'm creeping up on you / 我正悄悄靠近你
- **v1-8**: (upbeat music) / （欢快的音乐）

## 视频2（4条字幕）

### 基本信息
| 字段 | 值 |
|---|---|
| 字幕条数 | 4 |
| 处理版本 | sandbox-web-search-v1 |
| 歌曲状态 | UNCONFIRMED |
| 歌曲 | So Long, London - Taylor Swift |
| 置信度 | N/A |

### 搜索诊断
| 轮次 | 识别歌曲 | 匹配率 | 分类 | 搜索次数 |
|---|---|---|---|---|
| R1 | (解析失败) | N/A | PARSE_FAILED | 16 |
| R2 | So Long, London - Taylor Swift | 50.0% | MIDDLE_ZONE | 3 |

### 输出字幕
- **v2-0**: For so long, London / 伦敦，这么久以来
- **v2-1**: I saw the lights go out / 我看见灯火熄灭
- **v2-2**: In your eyes, in your eyes / 在你眼中，在你眼中
- **v2-3**: So long, London / 再见了，伦敦

## 视频3（5条字幕）

### 基本信息
| 字段 | 值 |
|---|---|
| 字幕条数 | 5 |
| 处理版本 | sandbox-web-search-v1 |
| 歌曲状态 | NOT_FOUND |
| 歌曲 | N/A - N/A |
| 置信度 | N/A |

### 搜索诊断
| 轮次 | 识别歌曲 | 匹配率 | 分类 | 搜索次数 |
|---|---|---|---|---|
| R1 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R2 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R3 | Love in the Dark - Adele | 40.0% | RESEARCH | 7 |
| R4 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R5 | (解析失败) | N/A | PARSE_FAILED | 11 |
| R6 | (解析失败) | N/A | PARSE_FAILED | 11 |

### 输出字幕
- **v3-0**: Take your eyes off of me / 把你的目光从我身上移开
- **v3-1**: So I can leave / 这样我才能离开
- **v3-2**: I've been trying to forget you / 我一直在努力忘记你
- **v3-3**: But you're always on my mind / 但你始终萦绕在我心头
- **v3-4**: Eyes on me / 看着我

## 汇总
| 视频 | 字幕数 | 搜索轮次 | 最终分类 | 歌曲 | 状态 |
|---|---|---|---|---|---|
| 视频1（9条字幕） | 9 | 6 | PARSE_FAILED | N/A | NOT_FOUND |
| 视频2（4条字幕） | 4 | 2 | MIDDLE_ZONE | So Long, London | UNCONFIRMED |
| 视频3（5条字幕） | 5 | 6 | PARSE_FAILED | N/A | NOT_FOUND |
