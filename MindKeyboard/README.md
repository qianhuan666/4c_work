# MindInput 键盘 - 技术文档

## 一、项目概述

### 1.1 项目定位

MindInput 是一款基于 Android IME（自定义输入法）的心理健康状态检测与干预系统。通过分析用户打字行为和文本内容，在不侵犯隐私的前提下，实现心理风险的早期预警和温和干预。

### 1.2 核心特点

- **非侵入式**：无需用户主动填表，在日常打字中完成检测
- **端侧处理**：所有文本分析在本地完成，原文永不离开设备
- **多维度信号融合**：打字行为 + 文本情感 + 认知扭曲检测
- **分级干预**：从"休息提醒"到"危机热线"，逐级递进

### 1.3 技术栈

| 层级 | 技术选型 |
|------|---------|
| 平台 | Android IME (InputMethodService) |
| 语言 | Kotlin 1.9 |
| 端侧推理 | ONNX Runtime Android 1.16 |
| 安全存储 | Android Keystore (AES-256-GCM) |
| 异步 | Kotlin Coroutines |
| 网络 | OkHttp + Retrofit |
| 加密 | AndroidX Security-Crypto |

---

## 二、项目结构

```
MindKeyboard/
├── app/
│   └── src/main/
│       ├── java/com/mindinput/
│       │   │
│       │   ├── MindInputService.kt          # [核心] IME 主服务
│       │   ├── MindKeyboardView.kt          # [核心] 键盘视图与按键处理
│       │   │
│       │   ├── collector/                   # 数据收集层
│       │   │   ├── TypingBehaviorCollector.kt   # 打字行为特征收集
│       │   │   └── TextSubmitListener.kt        # 文本提交事件监听
│       │   │
│       │   ├── analyzer/                    # 端侧分析层
│       │   │   ├── LocalTextAnalyzer.kt        # 文本分析入口
│       │   │   ├── CrisisDetector.kt            # 危机词实时检测
│       │   │   └── SentimentClassifier.kt      # ONNX 情感分类
│       │   │
│       │   ├── detector/                    # 状态检测层
│       │   │   ├── MentalStateDetector.kt       # 风险判定核心
│       │   │   ├── BaselineManager.kt           # 个人基线管理
│       │   │   └── ScoringEngine.kt            # 评分计算引擎
│       │   │
│       │   ├── intervention/                # 干预层
│       │   │   ├── InterventionEngine.kt        # 干预决策中心
│       │   │   ├── L1RestReminder.kt           # L1: 休息提醒
│       │   │   ├── L2ConversationCard.kt        # L2: 对话卡片
│       │   │   └── L4CrisisDialog.kt          # L4: 危机热线
│       │   │
│       │   ├── storage/                     # 存储层
│       │   │   └── SecureStorage.kt            # Keystore 加密存储
│       │   │
│       │   ├── sync/                        # 同步层
│       │   │   └── CloudSyncService.kt         # 云端匿名数据同步
│       │   │
│       │   └── model/                       # 数据模型层
│       │       ├── TypingFeatures.kt           # 打字特征
│       │       ├── TextFeatures.kt             # 文本特征
│       │       ├── MentalState.kt              # 心理状态
│       │       ├── BaselineData.kt            # 基线数据
│       │       └── UploadPayload.kt           # 上报数据格式
│       │
│       ├── res/                             # 资源文件
│       │   ├── layout/
│       │   │   ├── keyboard_view.xml         # 键盘主布局
│       │   │   ├── l1_reminder_bar.xml       # L1 提醒条
│       │   │   └── l2_conversation_card.xml  # L2 对话卡片
│       │   │
│       │   ├── drawable/
│       │   │   ├── key_background.xml         # 按键背景
│       │   │   ├── key_special_background.xml # 特殊按键背景
│       │   │   └── l2_card_background.xml    # L2 卡片背景
│       │   │
│       │   ├── values/
│       │   │   ├── strings.xml               # 字符串资源
│       │   │   ├── colors.xml                # 颜色定义
│       │   │   └── themes.xml                # 主题样式
│       │   │
│       │   └── xml/
│       │       └── method.xml                # IME 配置
│       │
│       └── assets/
│           └── sentiment_model.onnx          # 端侧情感模型 (~8MB)
│
├── build.gradle.kts                         # 根构建配置
├── settings.gradle.kts                     # 项目设置
└── gradle.properties                       # Gradle 属性
```

---

## 三、核心模块详解

### 3.1 MindInputService（IME 主服务）

**文件位置**: `com.mindinput.MindInputService`

**职责**:
1. 实现 `InputMethodService` 接口，作为输入法与系统的桥梁
2. 管理键盘视图的生命周期
3. 协调各模块（收集器→分析器→检测器→干预引擎）的数据流转

**核心流程**:
```
用户按键 → MindKeyboardView 捕获
        ↓
 TypingBehaviorCollector 记录行为特征
        ↓
 累积文本达到阈值 → LocalTextAnalyzer 分析文本
        ↓
 MentalStateDetector 融合评分
        ↓
 InterventionEngine 决定干预等级
        ↓
 触发对应干预 UI
```

**关键方法**:
| 方法 | 说明 |
|------|------|
| `onCreateInputView()` | 创建键盘视图 |
| `onStartInput()` | 输入开始，初始化状态 |
| `onFinishInput()` | 输入结束，触发最终分析 |
| `handleKeyEvent()` | 处理按键事件 |
| `handleTextSubmitted()` | 处理文本提交 |

### 3.2 TypingBehaviorCollector（打字行为收集器）

**文件位置**: `com.mindinput.collector.TypingBehaviorCollector`

**设计目标**: 高性能、低内存占用的行为特征采集

**采集指标**:

| 指标 | 计算方式 | 心理学含义 |
|------|---------|-----------|
| **WPM** | 60s / 平均按键间隔 | 速度异常下降 → 疲惫/低落 |
| **退格率** | 退格次数 / 总按键 | 过高 → 焦虑；过低 → 冲动 |
| **长停顿次数** | 间隔 > 3s 的次数 | 思维卡壳、犹豫不决 |
| **节奏方差** | 按键间隔标准差 | 方差大 → 情绪不稳定 |
| **夜间打字** | 23:00-04:00 | 失眠信号 |
| **会话时长** | 最后键 - 首次键 | 编辑/思考时间 |

**性能优化**:
- **环形队列**: 使用 `LongArray` 而非 `List`，避免 GC
- **滑动窗口**: 仅保留最近 5 分钟数据
- **O(1) 入队**: 环形队列实现

**关键代码**:
```kotlin
class TypingBehaviorCollector {
    private val keyTimestamps = LongArray(MAX_QUEUE_SIZE)  // 4096 容量
    private var head = 0
    private var size = 0

    // O(1) 入队
    fun recordKeyEvent(timestamp: Long, keyCode: Int) {
        val index = (head + size) % MAX_QUEUE_SIZE
        keyTimestamps[index] = timestamp
        if (size < MAX_QUEUE_SIZE) size++ else head = (head + 1) % MAX_QUEUE_SIZE
    }
}
```

### 3.3 LocalTextAnalyzer（端侧文本分析）

**文件位置**: `com.mindinput.analyzer.LocalTextAnalyzer`

**分析层次**（优先级从高到低）:

```
第一层：危机词检测（本地实时拦截，不过云）
├── "不想活" / "没意思" / "消失"
└── 触发：立即本地弹窗

第二层：认知扭曲关键词
├── 绝望感：算了、放弃、没用
├── 灾难化：完了、毁了、怎么办
├── 绝对化：永远、总是、从来不
└── 自我否定：我太差、我不行

第三层：ONNX 情感分类
├── 输入：分词 + tokenization
├── 输出：0.0(负面) ~ 1.0(正面)
└── 模型：TextCNN (~8MB)

第四层：语言学特征
├── 一人称密度："我"字占比
├── 积极词汇密度
└── 标点符号模式
```

**隐私设计**:
```kotlin
// 只返回特征，绝不存储原文
data class TextFeatures(
    val sentimentScore: Float,      // ✅ 情感得分
    val distortionFlags: Int,       // ✅ 认知扭曲类型
    val isCrisis: Boolean,           // ✅ 危机标记
    // ❌ 绝无 rawText 字段
)
```

### 3.4 MentalStateDetector（心理状态检测核心）

**文件位置**: `com.mindinput.detector.MentalStateDetector`

**检测流程**:
```
输入:
├── TypingFeatures (打字行为)
└── TextFeatures (文本特征)
        ↓
获取个人基线 (BaselineManager)
        ↓
计算 Z-scores (ScoringEngine)
├── 打字速度 Z = (μ - x) / σ
├── 退格率 Z = (x - μ) / σ
├── 情感 Z = (μ - score) / σ
└── ...
        ↓
加权融合
score = Σ(Z × weight) + consecutiveDays × 5
        ↓
EMA 平滑
S_t = α × new + (1-α) × S_{t-1}
        ↓
输出: MentalState (等级 + 评分 + 风险标签)
```

**风险等级**:

| 等级 | 阈值 | 干预方式 |
|------|------|---------|
| L0 | < 35 | 无干预 |
| L1 | 35-55 | 休息提醒 |
| L2 | 55-75 | 对话卡片 |
| L3 | 75-90 | 通知辅导员 |
| L4 | > 90 或危机词 | 危机热线 |

**风险标签**:
- `INSOMNIA_TENDENCY` - 失眠倾向
- `DEPRESSION_TENDENCY` - 抑郁倾向
- `SOCIAL_WITHDRAWAL` - 社会退缩
- `EMOTIONAL_DYSREGULATION` - 情绪失调
- `BURNOUT` - 身心耗竭

### 3.5 InterventionEngine（干预引擎）

**文件位置**: `com.mindinput.intervention.InterventionEngine`

**分级干预策略**:

```
L4 危机热线
┌─────────────────────────────────────┐
│  ╔═══════════════════════════════╗  │
│  ║     你还好吗？                 ║  │
│  ║                               ║  │
│  ║  我注意到你可能正在经历困难。  ║  │
│  ║  你现在安全吗？               ║  │
│  ║                               ║  │
│  ║  [我想聊聊]  [我没事]          ║  │
│  ╚═══════════════════════════════╝  │
└─────────────────────────────────────┘
不可关闭，直接拨打 400-161-9995

L2 对话卡片
┌─────────────────────────────────────┐
│  💙 听起来今天有点难熬              │
│                                     │
│  要聊聊是什么事吗？                │
│  或者我陪你做个深呼吸？            │
│                                     │
│  [聊一聊]  [深呼吸]  [不用]        │
└─────────────────────────────────────┘

L1 休息提醒
┌─────────────────────────────────────┐
│ 🌿 你已连续打字 40 分钟，休息一下？│
│                        [好的] [稍后] │
└─────────────────────────────────────┘
```

**冷却机制**: 同一等级干预触发后，需等待 5 分钟才能再次触发同等级或更低等级干预。

---

## 四、数据模型

### 4.1 TypingFeatures（打字特征）

```kotlin
data class TypingFeatures(
    val typingSpeed: Float,      // WPM (0-200)
    val backspaceRate: Float,    // 退格率 (0.0-1.0)
    val longPauseCount: Int,     // 长停顿次数
    val rhythmVariance: Float,   // 节奏方差
    val isLateNight: Boolean,    // 夜间打字
    val sessionDuration: Long,  // 会话时长(ms)
    val totalKeyCount: Int,      // 总按键数
    val timestamp: Long          // 提取时间
)
```

### 4.2 TextFeatures（文本特征）

```kotlin
data class TextFeatures(
    val sentimentScore: Float,           // 情感得分 (0.0-1.0)
    val sentimentCategory: Int,          // -1负面 / 0中性 / 1正面
    val distortionFlags: Int,            // 位标志 (bit0=绝望感...)
    val firstPersonDensity: Float,       // 一人称密度
    val positiveSignalCount: Int,         // 积极信号数
    val isCrisis: Boolean,               // 危机标记
    val distortionTypes: List<DistortionType>,
    val textLength: Int
)
```

### 4.3 MentalState（心理状态）

```kotlin
data class MentalState(
    val level: AnxietyLevel,            // L0-L4
    val score: Float,                    // 综合评分 (0-100)
    val behaviorAnomalyScore: Float,     // 行为异常得分
    val textNegativeScore: Float,        // 文本负面得分
    val fatigueScore: Float,            // 疲劳度
    val consecutiveAlertDays: Int,       // 连续异常天数
    val riskTags: List<RiskTag>,         // 风险标签
    val timestamp: Long
)

enum class AnxietyLevel { L0_NORMAL, L1_LOW, L2_MEDIUM, L3_HIGH, L4_CRISIS }
enum class RiskTag { INSOMNIA_TENDENCY, DEPRESSION_TENDENCY, ... }
```

### 4.4 BaselineData（基线数据）

```kotlin
data class BaselineData(
    val userId: String,
    val avgTypingSpeed: Float,          // 打字速度均值
    val stdTypingSpeed: Float,           // 打字速度标准差
    val avgBackspaceRate: Float,         // 退格率均值
    val stdBackspaceRate: Float,
    val avgSentiment: Float,             // 情感均值
    val stdSentiment: Float,
    val dataPoints: Int,                 // 数据点数
    val isPersonalBaseline: Boolean,    // 是否已切换个人基线
    val lastUpdateTime: Long
)
// 新用户使用群体基线，100+ 数据点后切换个人基线
```

### 4.5 UploadPayload（上报数据）

```kotlin
data class UploadPayload(
    val anonymousId: String,             // SHA-256 哈希，非真实ID
    val hourOfDay: Int,                  // 精确到小时
    val dayOfWeek: Int,
    val typingSpeedZ: Float,            // Z-score
    val backspaceZ: Float,
    val sentimentScore: Float,          // 0-1
    val distortionFlags: Int,           // 位标志
    val anxietyLevel: Int,              // 0-4
    val consecutiveAlertDays: Int,
    val riskScore: Float,
    val timestamp: Long
)
// ❌ 绝无任何原始文本
```

---

## 五、安全设计

### 5.1 端侧处理原则

```
用户输入文本
    ↓
LocalTextAnalyzer.analyze(text)
    ↓
仅提取特征，不存储原文
    ↓
上报: UploadPayload(匿名ID + Z-scores + 情感得分)
    ↓
云端：无法还原原始文本
```

### 5.2 Android Keystore 加密

```kotlin
class SecureStorage {
    // 使用 Android Keystore 生成密钥
    // AES-256-GCM 加密
    // 即使设备被 root，也无法读取加密数据
}
```

### 5.3 数据去标识化

- **用户 ID**: `SHA-256(设备ID + salt)`，非真实账号
- **时间精度**: 精确到小时，而非分钟/秒
- **数值特征**: Z-score 而非原始值
- **文本特征**: 位标志而非具体文字

---

## 六、云端同步

### 6.1 CloudSyncService

```kotlin
class CloudSyncService {
    // 队列机制：减少网络请求
    // 批量上报：最多 100 条/次
    // 重试机制：失败后加入重试队列
}
```

### 6.2 上报数据用途

1. **群体基线计算**: 新用户首次安装使用
2. **趋势分析**: 匿名统计，改进模型
3. **跨设备同步**: 用户换手机时可迁移基线

---

## 七、关键算法

### 7.1 Z-score 计算

```
Z = (x - μ) / σ

打字速度：值越小（越慢）→ Z 越大（异常）
情感得分：值越小（越负面）→ Z 越大（异常）
```

### 7.2 EMA 平滑

```
S_t = α × score_t + (1-α) × S_{t-1}

α = 0.2
- 平衡灵敏度与稳定性
- 避免单次噪声触发误报
```

### 7.3 综合评分公式

```
综合得分 = Σ(各维度Z分数 × 权重) + 连续异常天数 × 5

权重分配:
- 打字速度: 20%
- 退格率: 15%
- 夜间打字: 10%
- 节奏方差: 10%
- 情感得分: 25%
- 认知扭曲: 15%
- 一人称密度: 5%
```

---

## 八、依赖配置

### build.gradle.kts

```kotlin
dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // ONNX Runtime（端侧推理）
    implementation("ai.onnxruntime:onnxruntime-android:1.16.0")

    // Security Crypto（Keystore）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 网络
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### AndroidManifest.xml 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

---

## 九、开发路线图

### Phase 1：基础版（4-6周）
- [x] MindInputService 基础框架
- [x] QWERTY 键盘布局
- [x] TypingBehaviorCollector
- [ ] L0/L1 干预
- [ ] SharedPreferences 基线存储

### Phase 2：智能版（3-4周）
- [ ] ONNX 模型集成
- [ ] LocalTextAnalyzer 完整实现
- [ ] CrisisDetector 危机词检测
- [ ] L2/L4 干预
- [ ] SecureStorage Keystore 加密

### Phase 3：云端版（2-3周）
- [ ] CloudSyncService
- [ ] 群体基线模型
- [ ] 跨设备同步
- [ ] 周报功能

---

## 十、注意事项

### 10.1 安装与启用

1. 安装 APK 后，进入「设置 → 系统 → 语言与输入法 → 键盘」
2. 启用 "MindInput 键盘"
3. 首次使用时会请求 "允许完全访问"（用于网络同步，可选拒绝）

### 10.2 模型文件

将训练好的 ONNX 模型放入 `app/src/main/assets/sentiment_model.onnx`。如暂无模型，系统会使用基于关键词的 fallback 情感分析器。

### 10.3 测试建议

- 使用真机测试，模拟器对 IME 支持有限
- 重点测试：按键响应速度、干预弹窗触发、数据上报格式

---

## 十一、License

MIT License
