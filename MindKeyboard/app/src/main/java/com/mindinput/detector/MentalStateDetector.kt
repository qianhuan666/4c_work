package com.mindinput.detector

import android.content.Context
import com.mindinput.model.MentalState
import com.mindinput.model.TextFeatures
import com.mindinput.model.TypingFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 心理健康状态检测核心
 *
 * 职责：
 * 1. 融合打字行为特征和文本特征
 * 2. 与个人基线对比计算 Z-score
 * 3. EMA 平滑避免单次噪声
 * 4. 综合判定风险等级
 */
class MentalStateDetector(context: Context) {

    private val baselineManager = BaselineManager(context)
    private val scoringEngine = ScoringEngine()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // EMA 平滑参数
    private var lastSmoothedScore: Float = 0f
    private var lastBehaviorScore: Float = 0f
    private var lastTextScore: Float = 0f
    private var consecutiveAlertDays: Int = 0
    private var lastAlertDay: Long = 0L

    companion object {
        private const val EMA_ALPHA = 0.2f  // 平滑系数，越大越敏感
        private const val MILLIS_PER_DAY = 86400000L
    }

    // 回调接口
    var onStateChanged: ((MentalState) -> Unit)? = null

    /**
     * 评估当前心理状态
     * @param typingFeatures 打字行为特征
     * @param textFeatures 文本特征
     */
    fun evaluate(typingFeatures: TypingFeatures, textFeatures: TextFeatures) {
        scope.launch {
            val state = evaluateAsync(typingFeatures, textFeatures)
            onStateChanged?.invoke(state)
        }
    }

    /**
     * 评估心理状态（异步）
     */
    suspend fun evaluateAsync(
        typingFeatures: TypingFeatures,
        textFeatures: TextFeatures
    ): MentalState = withContext(Dispatchers.Default) {

        // 如果检测到危机，直接返回最高风险
        if (textFeatures.isCrisis) {
            consecutiveAlertDays++
            lastAlertDay = System.currentTimeMillis()

            return@withContext MentalState(
                level = com.mindinput.model.AnxietyLevel.L4_CRISIS,
                score = 100f,
                behaviorAnomalyScore = 0f,
                textNegativeScore = 100f,
                fatigueScore = 0f,
                consecutiveAlertDays = consecutiveAlertDays,
                riskTags = listOf(com.mindinput.model.RiskTag.DEPRESSION_TENDENCY),
                timestamp = System.currentTimeMillis()
            )
        }

        // 获取基线
        val baseline = baselineManager.getBaseline()

        // 计算 Z-scores
        val behaviorZ = scoringEngine.calculateBehaviorZScores(typingFeatures, baseline)
        val textZ = scoringEngine.calculateTextZScores(textFeatures, baseline)

        // 计算异常得分
        val behaviorAnomalyScore = calculateBehaviorAnomalyScore(behaviorZ)
        val textNegativeScore = calculateTextNegativeScore(textZ)

        // 计算综合风险评分
        val rawScore = scoringEngine.calculateRiskScore(
            behaviorZ,
            textZ,
            consecutiveAlertDays,
            textFeatures.isCrisis
        )

        // EMA 平滑
        val smoothedScore = emaSmooth(rawScore)

        // 确定风险等级
        val level = scoringEngine.determineLevel(smoothedScore)

        // 更新连续异常天数
        updateConsecutiveDays(level)

        // 识别风险标签
        val riskTags = scoringEngine.identifyRiskTags(behaviorZ, textZ, typingFeatures)

        // 更新基线（异步，不阻塞）
        launch { baselineManager.updateBaseline(typingFeatures, textFeatures) }

        MentalState(
            level = level,
            score = smoothedScore,
            behaviorAnomalyScore = behaviorAnomalyScore,
            textNegativeScore = textNegativeScore,
            fatigueScore = typingFeatures.longPauseCount.toFloat().coerceIn(0f, 10f) * 10,
            consecutiveAlertDays = consecutiveAlertDays,
            riskTags = riskTags,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * EMA 平滑
     */
    private fun emaSmooth(newScore: Float): Float {
        return if (lastSmoothedScore == 0f) {
            newScore
        } else {
            EMA_ALPHA * newScore + (1 - EMA_ALPHA) * lastSmoothedScore
        }.also { lastSmoothedScore = it }
    }

    /**
     * 计算行为异常得分
     */
    private fun calculateBehaviorAnomalyScore(z: ScoringEngine.BehaviorZScores): Float {
        return (
            z.typingSpeed * 0.4f +
            z.backspace * 0.3f +
            z.lateNight * 0.2f +
            z.rhythm * 0.1f
        ).coerceIn(0f, 100f)
    }

    /**
     * 计算文本负面得分
     */
    private fun calculateTextNegativeScore(z: ScoringEngine.TextZScores): Float {
        return (
            z.sentiment * 0.6f +
            z.distortion * 0.3f +
            z.firstPerson * 0.1f
        ).coerceIn(0f, 100f)
    }

    /**
     * 更新连续异常天数
     */
    private fun updateConsecutiveDays(level: com.mindinput.model.AnxietyLevel) {
        val now = System.currentTimeMillis()
        val today = now / MILLIS_PER_DAY
        val lastDay = lastAlertDay / MILLIS_PER_DAY

        when {
            level.value >= com.mindinput.model.AnxietyLevel.L1_LOW.value -> {
                if (today == lastDay) {
                    // 同一天，不增加天数
                } else if (today == lastDay + 1) {
                    consecutiveAlertDays++
                } else {
                    consecutiveAlertDays = 1
                }
                lastAlertDay = now
            }
            level.value == com.mindinput.model.AnxietyLevel.L0_NORMAL.value -> {
                // 正常则重置
                consecutiveAlertDays = 0
                lastAlertDay = now
            }
        }
    }

    /**
     * 获取用户匿名 ID
     */
    fun getUserId(): String = baselineManager.getUserId()

    /**
     * 重置检测状态
     */
    fun reset() {
        lastSmoothedScore = 0f
        lastBehaviorScore = 0f
        lastTextScore = 0f
        consecutiveAlertDays = 0
        lastAlertDay = 0L
    }
}
