package com.mindinput.model

/**
 * 打字行为特征数据类
 * 记录用户在一次打字会话中的行为信号
 */
data class TypingFeatures(
    /** 打字速度（WPM - Words Per Minute） */
    val typingSpeed: Float = 0f,

    /** 退格率（0.0 - 1.0） */
    val backspaceRate: Float = 0f,

    /** 长停顿次数（间隔 > 3秒） */
    val longPauseCount: Int = 0,

    /** 节奏方差（按键间隔标准差） */
    val rhythmVariance: Float = 0f,

    /** 是否在夜间打字（23:00 - 04:00） */
    val isLateNight: Boolean = false,

    /** 会话时长（毫秒） */
    val sessionDuration: Long = 0L,

    /** 总按键次数 */
    val totalKeyCount: Int = 0,

    /** 滑动窗口内按键次数 */
    val windowKeyCount: Int = 0,

    /** 提取特征的时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val LONG_PAUSE_THRESHOLD_MS = 3000L
        const val WINDOW_DURATION_MS = 5 * 60 * 1000L // 5分钟滑动窗口

        fun empty() = TypingFeatures()
    }

    fun isValid(): Boolean = totalKeyCount >= 10 && sessionDuration > 0
}
