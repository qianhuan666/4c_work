package com.mindinput

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.mindinput.analyzer.LocalTextAnalyzer
import com.mindinput.collector.TextSubmitListener
import com.mindinput.detector.MentalStateDetector
import com.mindinput.intervention.InterventionEngine
import com.mindinput.model.MentalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MindInput 输入法服务
 *
 * 核心职责：
 * 1. 实现 InputMethodService 接口
 * 2. 管理键盘视图
 * 3. 协调各模块（收集器、分析器、检测器、干预引擎）
 */
class MindInputService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 核心组件
    private var keyboardView: MindKeyboardView? = null
    private val textAnalyzer = LocalTextAnalyzer(this)
    private val stateDetector = MentalStateDetector(this)
    private val interventionEngine = InterventionEngine(this)

    // 当前输入连接
    private var currentInputConnection: InputConnection? = null

    // 当前累积文本（用于分析）
    private val currentTextBuilder = StringBuilder()
    private val textSubmitListener = TextSubmitListener { submittedText ->
        // 用户提交文本时调用
        onTextSubmitted(submittedText)
    }

    // 分析触发阈值
    private val analyzeThresholdChars = 10  // 累积 10 个字符后分析
    private val analyzeThresholdTime = 30_000L  // 或 30 秒后分析

    // 最后分析时间
    private var lastAnalyzeTime = 0L

    companion object {
        private const val TAG = "MindInputService"
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
    }

    private fun initializeComponents() {
        // 设置危机检测回调
        textAnalyzer.onCrisisDetected = { text ->
            // 危机检测本地拦截，不过云
            scope.launch {
                interventionEngine.processState(
                    MentalState(
                        level = com.mindinput.model.AnxietyLevel.L4_CRISIS,
                        score = 100f
                    ),
                    keyboardView?.typingCollector?.getCurrentSessionMinutes() ?: 0,
                    keyboardView?.getKeyboardBody()
                )
            }
        }

        // 设置状态变化回调
        stateDetector.onStateChanged = { state ->
            // 状态变化时处理干预
            interventionEngine.processState(
                state,
                keyboardView?.typingCollector?.getCurrentSessionMinutes() ?: 0,
                keyboardView?.getKeyboardBody()
            )
        }

        // 设置干预引擎回调
        interventionEngine.setL1Callbacks(
            onDismissed = { /* 用户选择稍后 */ },
            onAccepted = { /* 用户接受休息建议 */ }
        )

        interventionEngine.setL2Callbacks(
            onChat = { /* 用户选择聊天 */ },
            onBreath = { /* 用户选择深呼吸 */ },
            onDismissed = { /* 用户选择不用 */ }
        )

        interventionEngine.onL3Triggered = { state ->
            // L3 需要通知辅导员（需用户授权）
            // TODO: 实现通知逻辑
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = MindKeyboardView(this).apply {
            onKeyEvent = { keyCode, text ->
                handleKeyEvent(text)
            }

            onDeleteKey = {
                handleDeleteKey()
            }

            onTextSubmitted = { _ ->
                handleTextSubmitted()
            }
        }
        return keyboardView!!
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentInputConnection = currentInputConnection ?: currentInputConnection

        // 重置累积文本
        currentTextBuilder.clear()
        lastAnalyzeTime = System.currentTimeMillis()

        // 重置键盘状态
        keyboardView?.resetSession()
    }

    override fun onFinishInput() {
        super.onFinishInput()

        // 结束会话时做一次完整分析
        if (currentTextBuilder.isNotEmpty()) {
            analyzeCurrentText()
        }

        // 隐藏所有干预
        interventionEngine.hideAll()

        currentInputConnection = null
        currentTextBuilder.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        textAnalyzer.release()
        interventionEngine.release()
    }

    // ==================== 按键处理 ====================

    private fun handleKeyEvent(text: String) {
        val connection = currentInputConnection ?: return

        // 输入文本
        connection.commitText(text, 1)

        // 累积文本
        currentTextBuilder.append(text)

        // 检查是否应该触发分析
        checkAndTriggerAnalysis()
    }

    private fun handleDeleteKey() {
        val connection = currentInputConnection ?: return

        // 删除前一个字符
        connection.deleteSurroundingText(1, 0)

        // 从累积文本中移除最后一个字符
        if (currentTextBuilder.isNotEmpty()) {
            currentTextBuilder.deleteCharAt(currentTextBuilder.length - 1)
        }
    }

    private fun handleTextSubmitted() {
        // 用户点击发送/回车
        val submittedText = currentTextBuilder.toString()

        if (submittedText.isNotEmpty()) {
            // 提交文本到分析器
            textAnalyzer.analyzeAsync(submittedText)

            // 触发状态评估
            evaluateCurrentState()
        }

        // 重置累积文本
        currentTextBuilder.clear()
        lastAnalyzeTime = System.currentTimeMillis()
    }

    // ==================== 分析逻辑 ====================

    private fun checkAndTriggerAnalysis() {
        val now = System.currentTimeMillis()

        when {
            // 字符数达到阈值
            currentTextBuilder.length >= analyzeThresholdChars -> {
                if (now - lastAnalyzeTime >= analyzeThresholdTime / 3) {
                    analyzeCurrentText()
                    lastAnalyzeTime = now
                }
            }
            // 时间达到阈值
            now - lastAnalyzeTime >= analyzeThresholdTime -> {
                analyzeCurrentText()
                lastAnalyzeTime = now
            }
        }
    }

    private fun analyzeCurrentText() {
        val text = currentTextBuilder.toString()
        if (text.length < 3) return

        // 异步分析文本
        textAnalyzer.analyzeAsync(text)
    }

    private fun evaluateCurrentTextFeatures() {
        val text = currentTextBuilder.toString()
        if (text.length < 3) return

        scope.launch {
            // 获取打字特征
            val typingFeatures = keyboardView?.typingCollector?.extractFeatures()
                ?: return@launch

            // 分析文本
            val textFeatures = textAnalyzer.analyze(text)

            // 评估状态
            stateDetector.evaluate(typingFeatures, textFeatures)
        }
    }

    private fun evaluateCurrentState() {
        scope.launch {
            // 获取打字特征
            val typingFeatures = keyboardView?.typingCollector?.extractFeatures()
                ?: return@launch

            // 评估状态（使用空文本特征）
            val textFeatures = com.mindinput.model.TextFeatures()
            stateDetector.evaluate(typingFeatures, textFeatures)
        }
    }

    // ==================== IME 接口实现 ====================

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // 设置安全区域（键盘不遮挡内容）
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }
}
