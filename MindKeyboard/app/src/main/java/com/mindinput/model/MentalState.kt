package com.mindinput.model

/**
 * 心理健康风险等级枚举
 */
enum class AnxietyLevel(val value: Int, val threshold: Int) {
    /** 正常状态 */
    L0_NORMAL(0, 35),

    /** 低风险 */
    L1_LOW(1, 55),

    /** 中风险 */
    L2_MEDIUM(2, 75),

    /** 高风险 */
    L3_HIGH(3, 90),

    /** 紧急/危机 */
    L4_CRISIS(4, Int.MAX_VALUE);

    companion object {
        fun fromScore(score: Float): AnxietyLevel {
            return when {
                score >= L3_HIGH.threshold -> L4_CRISIS
                score >= L2_MEDIUM.threshold -> L3_HIGH
                score >= L1_LOW.threshold -> L2_MEDIUM
                score >= L0_NORMAL.threshold -> L1_LOW
                else -> L0_NORMAL
            }
        }
    }
}

/**
 * 综合心理状态评估结果
 */
data class MentalState(
    /** 当前风险等级 */
    val level: AnxietyLevel = AnxietyLevel.L0_NORMAL,

    /** 综合评分（0-100） */
    val score: Float = 0f,

    /** 打字行为异常得分 */
    val behaviorAnomalyScore: Float = 0f,

    /** 表情/文本负面得分 */
    val textNegativeScore: Float = 0f,

    /** 疲劳度得分 */
    val fatigueScore: Float = 0f,

    /** 连续异常天数 */
    val consecutiveAlertDays: Int = 0,

    /** 风险类型标签 */
    val riskTags: List<RiskTag> = emptyList(),

    /** 评估时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun normal() = MentalState(
            level = AnxietyLevel.L0_NORMAL,
            score = 0f
        )
    }
}

/**
 * 风险类型标签
 */
enum class RiskTag {
    INSOMNIA_TENDENCY,      // 失眠倾向
    DEPRESSION_TENDENCY,    // 抑郁倾向
    SOCIAL_WITHDRAWAL,      // 社会退缩
    EMOTIONAL_DYSREGULATION,// 情绪失调
    ANXIETY_TENDENCY,       // 焦虑倾向
    BURNOUT,                // 身心耗竭
    LIFE_RHYTHM_DISORDER    // 生活节律紊乱
}
