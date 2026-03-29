package com.mindinput.detector

import com.mindinput.model.AnxietyLevel
import com.mindinput.model.BaselineData
import com.mindinput.model.RiskTag
import com.mindinput.model.TypingFeatures
import com.mindinput.model.TextFeatures
import kotlin.math.abs

/**
 * 评分引擎
 *
 * 负责：
 * 1. 计算各维度的 Z-score
 * 2. 加权融合多维度信号
 * 3. 输出综合风险评分
 */
class ScoringEngine {

    companion object {
        // 行为维度权重
        private const val W_TYPING_SPEED = 0.20f
        private const val W_BACKSPACE = 0.15f
        private const val W_LATE_NIGHT = 0.10f
        private const val W_RHYTHM = 0.10f

        // 文本维度权重
        private const val W_SENTIMENT = 0.25f
        private const val W_DISTORTION = 0.15f
        private const val W_FIRST_PERSON = 0.05f

        // 风险等级阈值
        private const val THRESHOLD_L1 = 35f
        private const val THRESHOLD_L2 = 55f
        private const val THRESHOLD_L3 = 75f
        private const val THRESHOLD_L4 = 90f

        // 连续异常天数加权
        private const val CONSECUTIVE_DAYS_WEIGHT = 5f

        // Z-score 上下限（防止极端值）
        private const val MAX_ZSCORE = 3f
    }

    /**
     * 计算打字行为的 Z-score
     */
    fun calculateBehaviorZScores(
        features: TypingFeatures,
        baseline: BaselineData
    ): BehaviorZScores {
        return BehaviorZScores(
            typingSpeed = clampZscore(
                (baseline.avgTypingSpeed - features.typingSpeed) / baseline.stdTypingSpeed
            ),
            backspace = clampZscore(
                (features.backspaceRate - baseline.avgBackspaceRate) / baseline.stdBackspaceRate
            ),
            lateNight = if (features.isLateNight) 1f else 0f,
            rhythm = clampZscore(features.rhythmVariance / baseline.stdTypingSpeed)
        )
    }

    /**
     * 计算文本的 Z-score
     */
    fun calculateTextZScores(
        features: TextFeatures,
        baseline: BaselineData
    ): TextZScores {
        return TextZScores(
            sentiment = clampZscore(
                (baseline.avgSentiment - features.sentimentScore) / baseline.stdSentiment
            ),
            distortion = features.distortionFlags.toFloat() / 7f, // 归一化
            firstPerson = clampZscore(
                (features.firstPersonDensity - 0.1f) / 0.05f // 假设正常一人称密度 0.1
            )
        )
    }

    /**
     * 计算综合风险评分
     * @param behaviorZScores 行为 Z-score
     * @param textZScores 文本 Z-score
     * @param consecutiveAlertDays 连续异常天数
     * @param isCrisis 是否检测到危机
     * @return 风险评分（0-100）
     */
    fun calculateRiskScore(
        behaviorZScores: BehaviorZScores,
        textZScores: TextZScores,
        consecutiveAlertDays: Int = 0,
        isCrisis: Boolean = false
    ): Float {
        // 如果检测到危机，直接返回最高分
        if (isCrisis) {
            return THRESHOLD_L4
        }

        // 行为异常得分
        val behaviorScore =
            behaviorZScores.typingSpeed * W_TYPING_SPEED +
            behaviorZScores.backspace * W_BACKSPACE +
            behaviorZScores.lateNight * W_LATE_NIGHT +
            behaviorZScores.rhythm * W_RHYTHM

        // 文本异常得分
        val textScore =
            textZScores.sentiment * W_SENTIMENT +
            textZScores.distortion * W_DISTORTION +
            textZScores.firstPerson * W_FIRST_PERSON

        // 综合得分（归一化到 0-100）
        val rawScore = behaviorScore * 40 + textScore * 60

        // 加上连续异常天数加权
        val adjustedScore = rawScore + consecutiveAlertDays * CONSECUTIVE_DAYS_WEIGHT

        return adjustedScore.coerceIn(0f, 99f)
    }

    /**
     * 确定风险等级
     */
    fun determineLevel(score: Float): AnxietyLevel {
        return AnxietyLevel.fromScore(score)
    }

    /**
     * 识别风险标签
     */
    fun identifyRiskTags(
        behaviorZScores: BehaviorZScores,
        textZScores: TextZScores,
        features: TypingFeatures
    ): List<RiskTag> {
        val tags = mutableListOf<RiskTag>()

        // 基于行为判断
        if (behaviorZScores.lateNight > 0.5f) {
            tags.add(RiskTag.INSOMNIA_TENDENCY)
        }

        if (behaviorZScores.typingSpeed > 1.5f) {
            tags.add(RiskTag.LIFE_RHYTHM_DISORDER)
        }

        // 基于文本判断
        if (textZScores.sentiment > 1.5f) {
            tags.add(RiskTag.DEPRESSION_TENDENCY)
        }

        if (textZScores.distortion > 0.5f) {
            tags.add(RiskTag.EMOTIONAL_DYSREGULATION)
        }

        // 综合判断
        if (behaviorZScores.typingSpeed > 1f && textZScores.sentiment > 1f) {
            tags.add(RiskTag.BURNOUT)
        }

        return tags.distinct()
    }

    /**
     * 限制 Z-score 范围
     */
    private fun clampZscore(z: Float): Float {
        return z.coerceIn(-MAX_ZSCORE, MAX_ZSCORE)
    }

    data class BehaviorZScores(
        val typingSpeed: Float,  // 速度下降 = 正值
        val backspace: Float,    // 退格增加 = 正值
        val lateNight: Float,    // 夜间打字 = 正值
        val rhythm: Float        // 节奏不稳 = 正值
    )

    data class TextZScores(
        val sentiment: Float,   // 负面情感 = 正值
        val distortion: Float,  // 认知扭曲 = 正值
        val firstPerson: Float   // 一人称过多 = 正值
    )
}
