# MindInput 技术架构详解

## 一、系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MindInput IME                                   │
│                           (Android InputMethodService)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                      MindKeyboardView                                 │    │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐               │    │
│  │  │   QWERTY│  │  Number │  │  Symbol │  │  Emoji  │               │    │
│  │  │ Keyboard│  │ Keyboard│  │ Keyboard│  │ Picker  │               │    │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘               │    │
│  │       │            │            │            │                      │    │
│  │       └────────────┴────────────┴────────────┘                      │    │
│  │                           │                                           │    │
│  │                    KeyEvent Flow                                      │    │
│  └───────────────────────────┼───────────────────────────────────────────┘    │
│                              │                                                │
│                              ▼                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                   TypingBehaviorCollector                              │    │
│  │                                                                       │    │
│  │   ┌─────────────────────────────────────────────────────────────┐    │    │
│  │   │                    Circular Queue                             │    │    │
│  │   │   [t1] [t2] [t3] ... [t4096]                                  │    │    │
│  │   │     ↑                      ↑                                  │    │    │
│  │   │   head                   tail                                 │    │    │
│  │   └─────────────────────────────────────────────────────────────┘    │    │
│  │                                                                       │    │
│  │   ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │    │
│  │   │ WPM Calc   │  │Backspace % │  │ Long Pause │  │Rhythm Var  │  │    │
│  │   └────────────┘  └────────────┘  └────────────┘  └────────────┘  │    │
│  │                                                                       │    │
│  │                         ↓  extractFeatures()                          │    │
│  │                                                                       │    │
│  │                  ┌──────────────────┐                                 │    │
│  │                  │ TypingFeatures   │                                 │    │
│  │                  │ - typingSpeed    │                                 │    │
│  │                  │ - backspaceRate  │                                 │    │
│  │                  │ - longPauseCount │                                 │    │
│  │                  │ - rhythmVariance │                                 │    │
│  │                  │ - isLateNight    │                                 │    │
│  │                  └──────────────────┘                                 │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                              │                                                │
│                              ▼                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                      LocalTextAnalyzer                                 │    │
│  │                                                                       │    │
│  │   ┌──────────────────────────────────────────────────────────────┐    │    │
│  │   │  Layer 1: CrisisDetector (最高优先级)                        │    │    │
│  │   │  - 不想活 / 消失 / 没意思  →  本地拦截，不上报               │    │    │
│  │   └──────────────────────────────────────────────────────────────┘    │    │
│  │                               │                                       │    │
│  │   ┌──────────────────────────────────────────────────────────────┐    │    │
│  │   │  Layer 2: DistortionDetector                                 │    │    │
│  │   │  - 绝望感 / 灾难化 / 绝对化 / 自我否定                        │    │    │
│  │   └──────────────────────────────────────────────────────────────┘    │    │
│  │                               │                                       │    │
│  │   ┌──────────────────────────────────────────────────────────────┐    │    │
│  │   │  Layer 3: ONNX SentimentClassifier                          │    │    │
│  │   │  - TextCNN (~8MB)                                           │    │    │
│  │   │  - 端侧推理，不联网                                         │    │    │
│  │   └──────────────────────────────────────────────────────────────┘    │    │
│  │                               │                                       │    │
│  │   ┌──────────────────────────────────────────────────────────────┐    │    │
│  │   │  Layer 4: LinguisticFeatures                                 │    │    │
│  │   │  - 一人称密度 / 积极词汇 / 标点模式                         │    │    │
│  │   └──────────────────────────────────────────────────────────────┘    │    │
│  │                                                                       │    │
│  │                         ↓  analyze(text)                              │    │
│  │                                                                       │    │
│  │                  ┌──────────────────┐                                 │    │
│  │                  │  TextFeatures     │                                 │    │
│  │                  │  - sentimentScore │                                 │    │
│  │                  │  - distortionFlags│                                 │    │
│  │                  │  - isCrisis       │                                 │    │
│  │                  └──────────────────┘                                 │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                              │                                                │
│                              ▼                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                    MentalStateDetector                                 │    │
│  │                                                                       │    │
│  │   ┌────────────────┐         ┌────────────────┐                       │    │
│  │   │ BaselineManager│         │ ScoringEngine  │                       │    │
│  │   │                │         │                │                       │    │
│  │   │ - 个人基线     │  ←───→  │ - Z-score 计算 │                       │    │
│  │   │ - 群体基线     │         │ - 权重融合     │                       │    │
│  │   │ - EMA 更新     │         │ - EMA 平滑     │                       │    │
│  │   └────────────────┘         └────────────────┘                       │    │
│  │                                                                       │    │
│  │                         ↓  evaluate()                                 │    │
│  │                                                                       │    │
│  │                  ┌──────────────────┐                                 │    │
│  │                  │  MentalState     │                                 │    │
│  │                  │  - level: L0-L4  │                                 │    │
│  │                  │  - score: 0-100  │                                 │    │
│  │                  │  - riskTags[]    │                                 │    │
│  │                  └──────────────────┘                                 │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                              │                                                │
│                              ▼                                                │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                    InterventionEngine                                 │    │
│  │                                                                       │    │
│  │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │    │
│  │   │ L4 Crisis   │  │ L3 High     │  │ L2 Medium   │  │ L1 Low    │ │    │
│  │   │ Dialog      │  │ (External)  │  │ Card        │  │ Reminder  │ │    │
│  │   │             │  │             │  │             │  │ Bar       │ │    │
│  │   │ 不可关闭    │  │ 通知辅导员  │  │ 对话卡片    │  │ 休息提示  │ │    │
│  │   │ 危机热线   │  │ (需授权)    │  │ 2秒延迟     │  │ 30min触发 │ │    │
│  │   └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘ │    │
│  │                                                                       │    │
│  │                         Cooldown: 5 min                               │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                           SecureStorage                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                      Android Keystore                                 │    │
│  │                                                                       │    │
│  │   ┌────────────┐         ┌────────────────────────────────────┐     │    │
│  │   │ KeyStore   │         │  EncryptedSharedPreferences         │     │    │
│  │   │ (AES-256) │  ←───→  │  - BaselineData (encrypted)         │     │    │
│  │   │            │         │  - Anonymous UserID                  │     │    │
│  │   └────────────┘         └────────────────────────────────────┘     │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                           CloudSyncService (Optional)                        │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                                                                       │    │
│  │   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐             │    │
│  │   │ Upload Queue│ →  │ OkHttp      │ →  │ Server      │             │    │
│  │   │ (max 100)   │    │ Client      │    │ /v1/upload  │             │    │
│  │   └─────────────┘    └─────────────┘    └─────────────┘             │    │
│  │           │                                                           │    │
│  │           ↓                                                           │    │
│  │   ┌─────────────────────────────────────────────────────────────┐   │    │
│  │   │  UploadPayload (Anonymous)                                   │   │    │
│  │   │  - anonymousId (SHA-256)                                     │   │    │
│  │   │  - typingSpeedZ / backspaceZ / sentimentScore                 │   │    │
│  │   │  - anxietyLevel / distortionFlags                             │   │    │
│  │   │  ❌ NO RAW TEXT                                               │   │    │
│  │   └─────────────────────────────────────────────────────────────┘   │    │
│  │                                                                       │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、数据流详解

### 2.1 按键事件完整流程

```
用户按下 'H' 键
    │
    ▼
MindKeyboardView.onKeyDown(keyCode=H)
    │
    ├── recordKeyEvent(timestamp, keyCode)
    │       │
    │       ├── keyTimestamps[ tail ] = timestamp
    │       ├── tail = (tail + 1) % MAX_QUEUE_SIZE
    │       └── totalKeyCount++
    │
    └── commitText("h", 1)
            │
            └── currentInputConnection.commitText("h", 1)

累积文本达到阈值 (10 字符)
    │
    ▼
analyzeCurrentText()
    │
    └── textAnalyzer.analyzeAsync(text)
            │
            ├── CrisisDetector.detectCrisis(text)
            │       └── 如果 isCrisis=true → 立即触发 L4
            │
            ├── CrisisDetector.detectDistortions(text)
            │       └── 返回 List<DistortionType>
            │
            ├── RuleBasedSentimentAnalyzer.analyze(text)
            │       └── 返回 0.0-1.0 情感得分
            │
            └── TextFeatures = (
                    sentimentScore,
                    distortionFlags,
                    isCrisis,
                    ...
                )
```

### 2.2 状态评估完整流程

```
定时触发 (每 30 秒) 或 文本提交时
    │
    ▼
evaluateCurrentTextFeatures()
    │
    ├── typingFeatures = keyboardCollector.extractFeatures()
    │       │
    │       ├── intervals = getIntervalsInWindow(5min)
    │       ├── WPM = 60 / avg(intervals) * 12
    │       ├── backspaceRate = backspaceCount / totalKeyCount
    │       ├── longPauseCount = count(intervals > 3000ms)
    │       ├── rhythmVariance = stddev(intervals)
    │       └── isLateNight = hour in [23,24] or [0,1,2,3,4]
    │
    └── stateDetector.evaluate(typingFeatures, textFeatures)
            │
            ├── baseline = baselineManager.getBaseline()
            │
            ├── behaviorZ = scoringEngine.calculateBehaviorZScores(features, baseline)
            │       ├── typingSpeedZ = (avgSpeed - speed) / stdSpeed
            │       ├── backspaceZ = (backspace - avgBack) / stdBack
            │       ├── lateNightZ = isLateNight ? 1 : 0
            │       └── rhythmZ = rhythmVariance / stdSpeed
            │
            ├── textZ = scoringEngine.calculateTextZScores(features, baseline)
            │       ├── sentimentZ = (avgSent - score) / stdSent
            │       ├── distortionZ = distortionFlags / 7
            │       └── firstPersonZ = (firstPersonDensity - 0.1) / 0.05
            │
            ├── rawScore = scoringEngine.calculateRiskScore(behaviorZ, textZ)
            │       └── = Σ(Z × weight) + consecutiveDays × 5
            │
            ├── smoothedScore = emaSmooth(rawScore)
            │       └── = 0.2 × rawScore + 0.8 × lastScore
            │
            ├── level = scoringEngine.determineLevel(smoothedScore)
            │       └── L0 if <35, L1 if <55, L2 if <75, L3 if <90, L4 otherwise
            │
            └── MentalState = (level, smoothedScore, riskTags, ...)
```

---

## 三、关键算法实现

### 3.1 环形队列 (Circular Queue)

```kotlin
class TypingBehaviorCollector {
    companion object {
        private const val MAX_QUEUE_SIZE = 4096
    }

    // 使用 primitive Array，避免对象头开销
    private val keyTimestamps = LongArray(MAX_QUEUE_SIZE)
    private var head = 0  // 指向最旧元素
    private var size = 0  // 当前元素数量

    /**
     * O(1) 入队操作
     */
    fun recordKeyEvent(timestamp: Long, keyCode: Int) {
        val tail = (head + size) % MAX_QUEUE_SIZE
        keyTimestamps[tail] = timestamp

        if (size < MAX_QUEUE_SIZE) {
            size++
        } else {
            // 队列满，移动 head 丢弃最旧元素
            head = (head + 1) % MAX_QUEUE_SIZE
        }
    }

    /**
     * 获取滑动窗口内的按键间隔
     */
    fun getIntervalsInWindow(windowMs: Long): List<Long> {
        val cutoff = System.currentTimeMillis() - windowMs
        val intervals = mutableListOf<Long>()
        var prev: Long? = null

        for (i in 0 until size) {
            val idx = (head + i) % MAX_QUEUE_SIZE
            val ts = keyTimestamps[idx]

            if (ts < cutoff) {
                // 过期数据：移动 head
                if (i < size / 2) {
                    head = (head + 1) % MAX_QUEUE_SIZE
                    size--
                }
                continue
            }

            prev?.let { intervals.add(ts - it) }
            prev = ts
        }

        return intervals
    }
}
```

### 3.2 Z-score 计算

```kotlin
class ScoringEngine {

    /**
     * 计算 Z-score 并限制范围
     * 防止极端值影响过大
     */
    private fun clampZscore(z: Float): Float {
        return z.coerceIn(-MAX_ZSCORE, MAX_ZSCORE)
    }

    /**
     * 打字速度 Z-score
     * 注意：速度下降（值变小）是负向异常，所以用 (μ - x) / σ
     */
    fun calculateTypingSpeedZ(
        speed: Float,
        baseline: BaselineData
    ): Float {
        val z = (baseline.avgTypingSpeed - speed) / baseline.stdTypingSpeed
        return clampZscore(z)  // 限制在 [-3, 3]
    }

    /**
     * 退格率 Z-score
     * 退格增加（值变大）是负向异常，所以用 (x - μ) / σ
     */
    fun calculateBackspaceZ(
        rate: Float,
        baseline: BaselineData
    ): Float {
        val z = (rate - baseline.avgBackspaceRate) / baseline.stdBackspaceRate
        return clampZscore(z)
    }

    /**
     * 情感得分 Z-score
     * 得分越低（越负面）是负向异常，所以用 (μ - score) / σ
     */
    fun calculateSentimentZ(
        score: Float,
        baseline: BaselineData
    ): Float {
        val z = (baseline.avgSentiment - score) / baseline.stdSentiment
        return clampZscore(z)
    }
}
```

### 3.3 EMA 平滑

```kotlin
class MentalStateDetector {

    private var lastSmoothedScore: Float = 0f

    companion object {
        private const val EMA_ALPHA = 0.2f  // 平滑系数
    }

    /**
     * EMA 平滑
     *
     * α 越大 → 对新值越敏感 → 响应快但噪声大
     * α 越小 → 对新值越迟钝 → 稳定但可能滞后
     *
     * S_t = α × X_t + (1-α) × S_{t-1}
     */
    private fun emaSmooth(newScore: Float): Float {
        return if (lastSmoothedScore == 0f) {
            // 首次计算，不做平滑
            newScore
        } else {
            EMA_ALPHA * newScore + (1 - EMA_ALPHA) * lastSmoothedScore
        }.also { lastSmoothedScore = it }
    }
}
```

### 3.4 危机词检测

```kotlin
class CrisisDetector {

    companion object {
        // 危机词列表（必须本地检测，不过云）
        private val CRISIS_KEYWORDS = listOf(
            // 自我伤害
            "不想活", "想死", "活不下去", "活着没意义",
            // 消失
            "消失", "消失掉", "想消失",
            // 无意义
            "没意思", "什么都没意思", "没有意义",
            // 绝望
            "绝望", "彻底绝望", "没希望了",
            // 极端自我否定
            "不配活着", "不如死了"
        )
    }

    /**
     * 检测危机信号
     * O(n) 字符串匹配，n = 关键词数量
     * 实际实现使用 Trie 树优化
     */
    fun detectCrisis(text: String): Boolean {
        val normalized = text.lowercase()
        return CRISIS_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
    }
}
```

---

## 四、内存与性能优化

### 4.1 内存优化策略

| 优化点 | 实现方式 | 效果 |
|--------|---------|------|
| 按键队列 | `LongArray(4096)` vs `List<Long>` | 减少 60% 内存 |
| 特征提取 | 按需计算，非实时 | 减少 CPU 占用 |
| 滑动窗口 | O(1) 过期数据清理 | 避免全量遍历 |
| ONNX 模型 | 单例 + 复用 session | 避免重复加载 |

### 4.2 性能指标目标

| 指标 | 目标 | 说明 |
|------|------|------|
| 按键响应延迟 | < 5ms | 不影响打字体验 |
| 特征分析耗时 | < 100ms | 后台异步执行 |
| ONNX 推理耗时 | < 50ms | TextCNN 模型 |
| 内存峰值 | < 50MB | 包含 ONNX 模型 |

---

## 五、隐私安全详解

### 5.1 数据分类

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户数据                                  │
├─────────────────┬───────────────────┬─────────────────────────┤
│   高敏感        │   中敏感           │   低敏感                 │
│   (绝不上云)    │   (端侧分析后上报)  │   (匿名统计)             │
├─────────────────┼───────────────────┼─────────────────────────┤
│ 原始输入文本    │ 打字速度 WPM       │ 匿名用户 ID             │
│ 聊天内容        │ 退格率             │ 时间段 (小时精度)       │
│ 搜索词          │ 情感得分           │ 风险等级 (0-4)          │
│ 社交动态        │ 认知扭曲类型       │ Z-score 值              │
│                 │ 夜间打字标记        │ 连续异常天数            │
└─────────────────┴───────────────────┴─────────────────────────┘
       ↓                    ↓                    ↓
    本地处理           本地分析后           仅特征值上报
    不存储             立即删除            无法还原原文
```

### 5.2 匿名化流程

```
用户真实设备 ID: "ABC123XYZ"
    │
    ├── 加 salt: "ABC123XYZ:mindinput_salt_2024"
    │
    ├── SHA-256 哈希: "a1b2c3d4e5f6..."
    │
    └── 生成匿名 ID: "a1b2c3d4"

目的: 即使云端数据泄露，也无法关联到真实用户
```

### 5.3 Keystore 加密

```kotlin
class SecureStorage {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    /**
     * AES-256-GCM 加密
     * - GCM 模式提供认证加密
     * - 密钥存储在 Android Keystore 硬件安全模块
     * - 即使设备被 root，只要用户未开启开发者选项就无法解密
     */
    fun saveEncrypted(data: ByteArray, key: String): Boolean {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // GCM 模式需要 IV
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)

        // 存储 IV + 密文
        val combined = iv + encrypted
        prefs.edit().putString(key, Base64.encodeToString(combined)).apply()
        return true
    }
}
```

---

## 六、云端 API 设计

### 6.1 上报接口

```
POST /v1/analytics/upload
Content-Type: application/json

Request:
{
    "anonymousId": "a1b2c3d4e5f6...",
    "hourOfDay": 23,
    "dayOfWeek": 3,
    "typingSpeedZ": 0.5,
    "backspaceZ": -0.3,
    "sentimentScore": 0.35,
    "distortionFlags": 5,
    "anxietyLevel": 2,
    "consecutiveAlertDays": 3,
    "riskScore": 58.5,
    "timestamp": 1711737600000
}

Response:
{
    "code": 200,
    "message": "success"
}
```

### 6.2 批量上报

```
POST /v1/analytics/batch_upload
Content-Type: application/json

Request:
{
    "payloads": [
        { ... },
        { ... },
        { ... }
    ]
}

Response:
{
    "code": 200,
    "message": "success",
    "accepted": 100
}
```

### 6.3 群体基线获取

```
GET /v1/baseline/group?version=1

Response:
{
    "avgTypingSpeed": 42.5,
    "stdTypingSpeed": 12.3,
    "avgBackspaceRate": 0.048,
    "stdBackspaceRate": 0.025,
    "avgSentiment": 0.52,
    "stdSentiment": 0.18,
    "version": 1,
    "updatedAt": "2024-03-01T00:00:00Z"
}
```

---

## 七、文件清单

### 7.1 核心模块文件

| 文件路径 | 行数 | 职责 |
|---------|------|------|
| `MindInputService.kt` | ~200 | IME 主服务，协调各模块 |
| `MindKeyboardView.kt` | ~180 | 键盘 UI，按键事件处理 |
| `TypingBehaviorCollector.kt` | ~200 | 打字行为采集，环形队列 |
| `LocalTextAnalyzer.kt` | ~120 | 端侧文本分析入口 |
| `CrisisDetector.kt` | ~180 | 危机词检测 |
| `SentimentClassifier.kt` | ~150 | ONNX 情感分类 |
| `MentalStateDetector.kt` | ~180 | 风险判定核心 |
| `BaselineManager.kt` | ~200 | 个人基线管理 |
| `ScoringEngine.kt` | ~180 | 评分计算 |
| `InterventionEngine.kt` | ~150 | 干预决策 |
| `L1RestReminder.kt` | ~100 | L1 干预组件 |
| `L2ConversationCard.kt` | ~120 | L2 干预组件 |
| `L4CrisisDialog.kt` | ~100 | L4 干预组件 |
| `SecureStorage.kt` | ~130 | 加密存储 |
| `CloudSyncService.kt` | ~200 | 云端同步 |

### 7.2 数据模型文件

| 文件路径 | 数据类 |
|---------|--------|
| `TypingFeatures.kt` | 打字特征 |
| `TextFeatures.kt` | 文本特征 |
| `MentalState.kt` | 心理状态 + AnxietyLevel + RiskTag |
| `BaselineData.kt` | 基线数据 |
| `UploadPayload.kt` | 上报数据 |

### 7.3 资源文件

| 文件路径 | 用途 |
|---------|------|
| `keyboard_view.xml` | QWERTY 键盘布局 |
| `l1_reminder_bar.xml` | L1 提醒条布局 |
| `l2_conversation_card.xml` | L2 对话卡片布局 |
| `key_background.xml` | 按键背景选择器 |
| `strings.xml` | 字符串资源 |
| `colors.xml` | 颜色定义 |
| `themes.xml` | 主题样式 |
| `method.xml` | IME 配置 |

---

## 八、测试建议

### 8.1 单元测试

```kotlin
// TypingBehaviorCollectorTest
@Test
fun `WPM calculation with known intervals`() {
    val collector = TypingBehaviorCollector()
    // 模拟 10 次按键，间隔 150ms (即 400 WPM)
    val baseTime = System.currentTimeMillis()
    for (i in 0..9) {
        collector.recordKeyEvent(baseTime + i * 150, 0)
    }

    val features = collector.extractFeatures()
    assertEquals(400f, features.typingSpeed, 10f)
}

// CrisisDetectorTest
@Test
fun `detect crisis keywords`() {
    val detector = CrisisDetector()
    assertTrue(detector.detectCrisis("我不想活了"))
    assertTrue(detector.detectCrisis("活着有什么意义"))
    assertFalse(detector.detectCrisis("今天天气真好"))
}

// ScoringEngineTest
@Test
fun `Z-score calculation`() {
    val engine = ScoringEngine()
    val baseline = BaselineData(
        avgTypingSpeed = 40f,
        stdTypingSpeed = 10f
    )

    // 速度 30 WPM (低于均值一个标准差)
    val z = engine.calculateTypingSpeedZ(30f, baseline)
    assertEquals(1f, z, 0.1f)
}
```

### 8.2 集成测试

1. 安装 APK，启用 MindInput 键盘
2. 在微信/备忘录等应用中打字
3. 观察：
   - 打字流畅度
   - L1 提醒是否在 30 分钟后出现
   - L2 卡片是否在输入负面内容后出现
   - 危机词是否触发 L4 弹窗

### 8.3 性能测试

```kotlin
// 性能基准测试
@Test
fun `extractFeatures performance`() {
    val collector = TypingBehaviorCollector()

    // 填充 4096 个事件
    val baseTime = System.currentTimeMillis()
    for (i in 0 until 4096) {
        collector.recordKeyEvent(baseTime + i * 100, 0)
    }

    val start = System.nanoTime()
    collector.extractFeatures()
    val duration = System.nanoTime() - start

    // 应该小于 10ms
    assertTrue(duration < 10_000_000)
}
```
