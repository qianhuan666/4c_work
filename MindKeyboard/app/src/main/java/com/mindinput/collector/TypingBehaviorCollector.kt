package com.mindinput.collector

import com.mindinput.model.TypingFeatures
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 打字行为特征收集器
 *
 * 使用环形队列实现高效的滑动窗口计算
 * 采集指标：打字速度、退格率、停顿次数、节奏方差等
 */
class TypingBehaviorCollector(
    private val windowDurationMs: Long = TypingFeatures.WINDOW_DURATION_MS,
    private val longPauseThresholdMs: Long = TypingFeatures.LONG_PAUSE_THRESHOLD_MS
) {
    companion object {
        private const val MAX_QUEUE_SIZE = 4096
        private const val MIN_KEYS_FOR_VALID_FEATURES = 10
    }

    // 环形队列存储按键时间戳
    private val keyTimestamps = LongArray(MAX_QUEUE_SIZE)
    private var head = 0
    private var size = 0

    // 当前会话统计
    private var sessionStartTime: Long = 0L
    private var firstKeyTime: Long = 0L
    private var lastKeyTime: Long = 0L
    private var totalKeyCount: Long = 0L
    private var backspaceCount: Long = 0L

    // 标记是否已开始会话
    private var isSessionActive = false

    /**
     * 记录一次按键事件
     * @param timestamp 事件时间戳
     * @param keyCode 按键码
     */
    fun recordKeyEvent(timestamp: Long, keyCode: Int) {
        // 启动新会话
        if (!isSessionActive) {
            sessionStartTime = timestamp
            firstKeyTime = timestamp
            isSessionActive = true
        }

        lastKeyTime = timestamp
        totalKeyCount++

        // 记录退格
        if (isBackspace(keyCode)) {
            backspaceCount++
        }

        // 入队（环形队列，O(1)）
        val index = (head + size) % MAX_QUEUE_SIZE
        keyTimestamps[index] = timestamp

        if (size < MAX_QUEUE_SIZE) {
            size++
        } else {
            // 队列满，移动头指针
            head = (head + 1) % MAX_QUEUE_SIZE
        }
    }

    /**
     * 记录文本提交事件（发送消息等）
     * 用于计算发送前编辑时长
     */
    fun onTextSubmitted(submittedText: String) {
        // 可以在这里记录编辑时长等额外信息
        // 当前实现暂时不处理
    }

    /**
     * 结束当前会话
     */
    fun endSession() {
        isSessionActive = false
    }

    /**
     * 提取当前打字行为特征
     * 在主线程调用可能阻塞，建议在后台线程执行
     */
    fun extractFeatures(): TypingFeatures {
        if (totalKeyCount < MIN_KEYS_FOR_VALID_FEATURES) {
            return TypingFeatures()
        }

        val intervals = getIntervalsInWindow(System.currentTimeMillis())
        val windowKeyCount = intervals.size + 1

        // 计算打字速度（WPM）
        val typingSpeed = calculateTypingSpeed(intervals)

        // 计算退格率
        val backspaceRate = if (totalKeyCount > 0) {
            backspaceCount.toFloat() / totalKeyCount.toFloat()
        } else 0f

        // 计算长停顿次数
        val longPauseCount = intervals.count { it > longPauseThresholdMs }

        // 计算节奏方差
        val rhythmVariance = calculateRhythmVariance(intervals)

        // 判断是否夜间打字
        val isLateNight = checkLateNight()

        // 会话时长
        val sessionDuration = if (firstKeyTime > 0 && lastKeyTime > 0) {
            lastKeyTime - firstKeyTime
        } else 0L

        return TypingFeatures(
            typingSpeed = typingSpeed,
            backspaceRate = backspaceRate,
            longPauseCount = longPauseCount,
            rhythmVariance = rhythmVariance,
            isLateNight = isLateNight,
            sessionDuration = sessionDuration,
            totalKeyCount = totalKeyCount.toInt(),
            windowKeyCount = windowKeyCount,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 重置所有统计数据
     */
    fun reset() {
        head = 0
        size = 0
        sessionStartTime = 0L
        firstKeyTime = 0L
        lastKeyTime = 0L
        totalKeyCount = 0L
        backspaceCount = 0L
        isSessionActive = false
    }

    /**
     * 获取滑动窗口内的按键间隔
     */
    private fun getIntervalsInWindow(currentTime: Long): List<Long> {
        val cutoff = currentTime - windowDurationMs
        val intervals = mutableListOf<Long>()
        var prevTime: Long? = null

        for (i in 0 until size) {
            val index = (head + i) % MAX_QUEUE_SIZE
            val timestamp = keyTimestamps[index]

            if (timestamp < cutoff) {
                // 清理过期数据
                if (i < size / 2) {
                    head = (head + 1) % MAX_QUEUE_SIZE
                    size--
                }
                continue
            }

            if (prevTime != null) {
                intervals.add(timestamp - prevTime)
            }
            prevTime = timestamp
        }

        return intervals
    }

    /**
     * 计算打字速度（WPM - Words Per Minute）
     * 假设平均单词长度为 5 个字符
     */
    private fun calculateTypingSpeed(intervals: List<Long>): Float {
        if (intervals.isEmpty()) return 0f

        val totalInterval = intervals.sum()
        if (totalInterval <= 0) return 0f

        // 计算每秒平均按键数，然后转换为 WPM
        val keysPerSecond = (intervals.size + 1).toFloat() / (totalInterval / 1000f)
        val wpm = keysPerSecond * (60 / 5) // 假设平均单词 5 字符

        return wpm.coerceIn(0f, 200f) // 合理范围限制
    }

    /**
     * 计算节奏方差（按键间隔标准差）
     */
    private fun calculateRhythmVariance(intervals: List<Long>): Float {
        if (intervals.size < 2) return 0f

        val mean = intervals.average()
        val variance = intervals.map { (it - mean).pow(2) }.average()

        return sqrt(variance).toFloat().coerceIn(0f, 10000f)
    }

    /**
     * 判断是否夜间打字（23:00 - 04:00）
     */
    private fun checkLateNight(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour <= 4
    }

    /**
     * 判断按键码是否为退格
     */
    private fun isBackspace(keyCode: Int): Boolean {
        return keyCode == android.view.KeyEvent.KEYCODE_DEL
    }

    /**
     * 获取当前会话时长（毫秒）
     */
    fun getCurrentSessionDuration(): Long {
        return if (isSessionActive && firstKeyTime > 0) {
            System.currentTimeMillis() - firstKeyTime
        } else 0L
    }

    /**
     * 获取当前会话时长（分钟）
     */
    fun getCurrentSessionMinutes(): Int {
        return (getCurrentSessionDuration() / 60000).toInt()
    }
}
