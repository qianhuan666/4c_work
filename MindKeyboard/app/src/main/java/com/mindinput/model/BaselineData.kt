package com.mindinput.model

/**
 * 个人基线数据
 * 用于计算 Z-score 异常检测
 */
data class BaselineData(
    val userId: String,

    /** 打字速度均值 */
    val avgTypingSpeed: Float = 40f,

    /** 打字速度标准差 */
    val stdTypingSpeed: Float = 10f,

    /** 退格率均值 */
    val avgBackspaceRate: Float = 0.05f,

    /** 退格率标准差 */
    val stdBackspaceRate: Float = 0.02f,

    /** 情感得分均值 */
    val avgSentiment: Float = 0.5f,

    /** 情感得分标准差 */
    val stdSentiment: Float = 0.15f,

    /** 夜间打字比例均值 */
    val avgLateNightRatio: Float = 0.1f,

    /** 数据点计数 */
    val dataPoints: Int = 0,

    /** 最后更新时间 */
    val lastUpdateTime: Long = System.currentTimeMillis(),

    /** 是否已切换到个人基线（vs 群体基线） */
    val isPersonalBaseline: Boolean = false
) {
    companion object {
        /** 群体基线默认值（首次安装使用） */
        fun defaultGroupBaseline(userId: String) = BaselineData(
            userId = userId,
            avgTypingSpeed = 40f,    // 一般人打字速度约 40-60 WPM
            stdTypingSpeed = 15f,
            avgBackspaceRate = 0.05f,
            stdBackspaceRate = 0.03f,
            avgSentiment = 0.5f,
            stdSentiment = 0.2f,
            avgLateNightRatio = 0.1f,
            dataPoints = 0,
            isPersonalBaseline = false
        )

        /** 切换到个人基线的最小数据点数 */
        const val MIN_DATA_POINTS_FOR_PERSONAL = 100
    }
}
