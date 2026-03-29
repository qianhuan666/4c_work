# MindInput 开发指南

## 一、环境准备

### 1.1 开发环境要求

| 项目 | 要求 |
|------|------|
| JDK | 17+ |
| Android Studio | Hedgehog (2023.1.1) 或更高 |
| Android SDK | API 26 (Android 8.0) 及以上 |
| Gradle | 8.2+ |
| Kotlin | 1.9.20 |

### 1.2 安装步骤

```bash
# 1. 克隆项目
git clone <repository-url>
cd MindKeyboard

# 2. 设置 Gradle Wrapper
gradle wrapper

# 3. 验证 Gradle Wrapper
./gradlew -v

# 4. 打开 Android Studio
# File -> Open -> 选择 MindKeyboard 目录

# 5. 等待 Gradle Sync 完成

# 6. 构建 Debug APK
./gradlew assembleDebug
```

### 1.3 运行到设备

```bash
# 连接设备后
./gradlew installDebug

# 查看设备日志
adb logcat | grep MindInput
```

---

## 二、项目导入 Android Studio

### 2.1 导入步骤

1. **File → Open**
2. 选择 `MindKeyboard` 文件夹
3. 等待 "Gradle Sync" 完成（约 2-5 分钟）
4. 选择 **app** 模块作为运行配置
5. 选择真机作为 Target Device（模拟器对 IME 支持有限）

### 2.2 首次配置

```
如果遇到 SDK 路径问题：
1. File → Project Structure → SDK Location
2. 设置 Android SDK 路径
3. 确保 Platform: API 34, Build Tools: 34.0.0
```

---

## 三、启用输入法

### 3.1 安装 APK

```bash
# 通过 ADB 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3.2 激活输入法

```
1. 打开设备「设置」
2. 进入「系统」→「语言与输入法」
3. 点击「屏幕键盘」
4. 找到「MindInput 键盘」，点击启用
5. 首次使用时会提示「允许完全访问」（可选拒绝）
```

### 3.3 设置为默认键盘

```
1. 进入「系统」→「语言与输入法」→「默认键盘」
2. 选择「MindInput 键盘」
```

### 3.4 测试输入法

```
1. 打开任何文本输入框（微信、备忘录等）
2. 切换到 MindInput 键盘
3. 开始打字，观察日志输出
```

---

## 四、关键代码位置

### 4.1 修改键盘布局

**文件**: `app/src/main/res/layout/keyboard_view.xml`

```xml
<!-- 例如：修改按键大小 -->
<Button
    android:id="@+id/key_a"
    style="@style/KeyStyle"
    android:layout_width="0dp"
    android:layout_height="48dp"  <!-- 改为 48dp -->
    ...
/>
```

### 4.2 添加新按键

**文件**: `app/src/main/res/layout/keyboard_view.xml`

```xml
<!-- 添加新按键 -->
<Button
    android:id="@+id/key_custom"
    style="@style/KeyStyle.Special"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:text="★"
    android:tag="star" />
```

**注册处理器**: `app/src/main/java/com/mindinput/MindKeyboardView.kt`

```kotlin
// 在 setupKeys() 中添加
findViewById<Button>(R.id.key_custom)?.setOnClickListener {
    // 处理自定义按键
}
```

### 4.3 修改危机词

**文件**: `app/src/main/java/com/mindinput/analyzer/CrisisDetector.kt`

```kotlin
// 在 CRISIS_KEYWORDS 中添加
private val CRISIS_KEYWORDS = listOf(
    "不想活", "想死",
    // 添加新词
    "好累啊", "撑不住了"  // ⚠️ 注意：不要添加过于日常的词汇，避免误报
)
```

### 4.4 修改风险等级阈值

**文件**: `app/src/main/java/com/mindinput/model/MentalState.kt`

```kotlin
enum class AnxietyLevel(val value: Int, val threshold: Int) {
    L0_NORMAL(0, 35),   // 修改这里调整阈值
    L1_LOW(1, 55),
    L2_MEDIUM(2, 75),
    L3_HIGH(3, 90),
    L4_CRISIS(4, Int.MAX_VALUE);
}
```

### 4.5 修改干预触发时机

**文件**: `app/src/main/java/com/mindinput/intervention/L1RestReminder.kt`

```kotlin
// 修改最少会话时长
fun setMinSessionMinutes(minutes: Int) {
    minSessionMinutes = minutes  // 默认 30 分钟
}
```

---

## 五、添加 ONNX 模型

### 5.1 准备模型

1. 训练一个情感分类模型（TextCNN 格式）
2. 导出为 ONNX 格式
3. 命名为 `sentiment_model.onnx`

### 5.2 放置模型

```bash
# 将模型放入 assets 目录
cp path/to/sentiment_model.onnx app/src/main/assets/
```

### 5.3 模型要求

| 参数 | 要求 |
|------|------|
| 格式 | ONNX (.onnx) |
| 输入名 | `input_ids` |
| 输出名 | `output` |
| 最大长度 | 128 tokens |
| 推荐大小 | < 10MB |

### 5.4 模型输入输出格式

```python
# 输入: tokenized text IDs
# Shape: [batch_size, seq_len] = [1, 128]
# dtype: int64

# 输出: sentiment probability
# Shape: [batch_size, 1] = [1, 1]
# dtype: float32
# 值域: 0.0 (非常负面) ~ 1.0 (非常正面)
```

---

## 六、调试技巧

### 6.1 查看日志

```bash
# 过滤 MindInput 相关日志
adb logcat | grep -E "MindInput|MindKeyboard|TypingBehavior"

# 查看所有日志
adb logcat

# 清空日志
adb logcat -c
```

### 6.2 常用日志 Tag

```kotlin
companion object {
    private const val TAG = "MindInputService"
    private const val TAG_COLLECTOR = "TypingCollector"
    private const val TAG_DETECTOR = "MentalDetector"
}
```

### 6.3 添加调试日志

```kotlin
android.util.Log.d("MindInput", "打字速度: ${features.typingSpeed} WPM")
```

### 6.4 模拟测试数据

```kotlin
// 在 MindInputService 中添加测试方法
private fun simulateTyping() {
    // 模拟连续打字 40 分钟
    scope.launch {
        for (i in 0 until 1000) {
            keyboardView?.typingCollector?.recordKeyEvent(
                System.currentTimeMillis(),
                0
            )
            delay(100)
        }
        // 触发分析
        evaluateCurrentState()
    }
}
```

---

## 七、常见问题

### Q1: Gradle Sync 失败

```
解决方案：
1. File → Invalidate Caches → Invalidate and Restart
2. 删除 .gradle 和 .idea 目录
3. 重新导入项目
```

### Q2: ONNX 模型加载失败

```
可能原因：
1. 模型文件未放入 assets 目录
2. 模型格式不兼容
3. 模型输入输出名称不匹配

解决方案：
- 检查 Logcat 中的 ONNX 相关错误
- 确认 assets/sentiment_model.onnx 文件存在
- 系统有基于关键词的 fallback，危机检测不受影响
```

### Q3: 键盘不显示

```
可能原因：
1. 输入法未启用
2. 系统版本低于 API 26

解决方案：
1. 确认已在系统设置中启用 MindInput 键盘
2. 尝试重启设备
3. 检查 Logcat 中的 MindInputService 初始化日志
```

### Q4: 干预弹窗不出现

```
可能原因：
1. 风险评分未达到触发阈值
2. 冷却期未过

解决方案：
1. 确认打字时间超过 30 分钟（触发 L1）
2. 输入包含危机词的文本（触发 L4）
3. 查看 Logcat 中的 InterventionEngine 日志
```

---

## 八、发布检查清单

### 8.1 功能检查

- [ ] 键盘可以正常打字
- [ ] 打字行为特征正确采集
- [ ] L1/L2/L4 干预可以触发
- [ ] 危机词检测正常
- [ ] 数据加密存储正常

### 8.2 隐私检查

- [ ] 确认无原始文本上报
- [ ] 确认上报数据已匿名化
- [ ] 用户拒绝"完全访问"后云端同步不可用
- [ ] 隐私政策已更新

### 8.3 性能检查

- [ ] 打字延迟 < 5ms
- [ ] APK 大小 < 30MB
- [ ] 内存占用 < 80MB

### 8.4 Play Store 检查

- [ ] 应用描述已更新（定位为"打字习惯分析工具"）
- [ ] 避免使用"监测"、"监控"等词汇
- [ ] 准备隐私政策页面
- [ ] 准备用户协议页面
