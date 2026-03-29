package com.mindinput.detector

import android.content.Context
import com.google.gson.Gson
import com.mindinput.model.BaselineData
import com.mindinput.model.TypingFeatures
import com.mindinput.model.TextFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 个人基线管理器
 *
 * 职责：
 * 1. 存储和管理用户个人打字行为基线
 * 2. 计算 Z-score 进行异常检测
 * 3. 在群体基线和个人基线之间切换
 */
class BaselineManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "mind_baseline"
        private const val KEY_BASELINE_DATA = "baseline_data"
        private const val KEY_USER_ID = "user_id"

        // EMA 平滑参数
        private const val ALPHA = 0.1f

        // 切换到个人基线的最小数据量
        private const val MIN_DATA_POINTS = 100
    }

    /**
     * 获取当前基线数据
     */
    suspend fun getBaseline(): BaselineData = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_BASELINE_DATA, null)
        if (json != null) {
            try {
                gson.fromJson(json, BaselineData::class.java)
            } catch (e: Exception) {
                BaselineData.defaultGroupBaseline(getOrCreateUserId())
            }
        } else {
            BaselineData.defaultGroupBaseline(getOrCreateUserId())
        }
    }

    /**
     * 获取用户匿名ID
     */
    fun getUserId(): String = getOrCreateUserId()

    private fun getOrCreateUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            // 生成随机匿名 ID（实际应使用设备 ID + salt 的哈希）
            userId = generateAnonymousId()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    private fun generateAnonymousId(): String {
        // 简化实现：使用随机 UUID
        // 实际应使用：SHA-256(设备ID + salt)
        return java.util.UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * 更新基线数据
     * 使用 EMA 方式平滑更新
     */
    suspend fun updateBaseline(
        typingFeatures: TypingFeatures,
        textFeatures: TextFeatures
    ) = withContext(Dispatchers.IO) {
        val current = getBaseline()

        // 计算新的统计数据
        val newDataPoints = current.dataPoints + 1
        val alpha = if (current.isPersonalBaseline) ALPHA else ALPHA * 0.5f

        // EMA 更新各项均值
        val newAvgSpeed = emaUpdate(current.avgTypingSpeed, typingFeatures.typingSpeed, alpha)
        val newAvgBackspace = emaUpdate(current.avgBackspaceRate, typingFeatures.backspaceRate, alpha)
        val newAvgSentiment = emaUpdate(current.avgSentiment, textFeatures.sentimentScore, alpha)

        // 更新标准差（简化：使用固定比例）
        val newStdSpeed = calculateRunningStd(
            current.stdTypingSpeed, current.avgTypingSpeed, newAvgSpeed,
            typingFeatures.typingSpeed, alpha
        )
        val newStdBackspace = calculateRunningStd(
            current.stdBackspaceRate, current.avgBackspaceRate, newAvgBackspace,
            typingFeatures.backspaceRate, alpha
        )
        val newStdSentiment = calculateRunningStd(
            current.stdSentiment, current.avgSentiment, newAvgSentiment,
            textFeatures.sentimentScore, alpha
        )

        val updated = current.copy(
            avgTypingSpeed = newAvgSpeed,
            stdTypingSpeed = newStdSpeed.coerceAtLeast(1f),
            avgBackspaceRate = newAvgBackspace,
            stdBackspaceRate = newStdBackspace.coerceAtLeast(0.001f),
            avgSentiment = newAvgSentiment,
            stdSentiment = newStdSentiment.coerceAtLeast(0.05f),
            dataPoints = newDataPoints,
            lastUpdateTime = System.currentTimeMillis(),
            isPersonalBaseline = newDataPoints >= MIN_DATA_POINTS
        )

        saveBaseline(updated)
    }

    /**
     * EMA 更新
     */
    private fun emaUpdate(current: Float, new: Float, alpha: Float): Float {
        return if (current == 0f) new else current + alpha * (new - current)
    }

    /**
     * 简化的标准差更新
     */
    private fun calculateRunningStd(
        currentStd: Float,
        currentMean: Float,
        newMean: Float,
        newValue: Float,
        alpha: Float
    ): Float {
        // 简化：标准差随数据量增加而缓慢收敛
        val baseStd = currentStd * 0.99f
        val valueContribution = kotlin.math.abs(newValue - newMean) * alpha * 0.1f
        return (baseStd + valueContribution).coerceAtLeast(1f)
    }

    /**
     * 保存基线数据
     */
    private fun saveBaseline(baseline: BaselineData) {
        val json = gson.toJson(baseline)
        prefs.edit().putString(KEY_BASELINE_DATA, json).apply()
    }

    /**
     * 重置基线（用户请求或重新开始）
     */
    suspend fun resetBaseline() = withContext(Dispatchers.IO) {
        val userId = getUserId()
        val newBaseline = BaselineData.defaultGroupBaseline(userId)
        saveBaseline(newBaseline)
    }

    /**
     * 获取基线状态描述
     */
    suspend fun getBaselineStatus(): BaselineStatus {
        val baseline = getBaseline()
        return BaselineStatus(
            isPersonal = baseline.isPersonalBaseline,
            dataPoints = baseline.dataPoints,
            daysSinceUpdate = ((System.currentTimeMillis() - baseline.lastUpdateTime) / 86400000).toInt()
        )
    }

    data class BaselineStatus(
        val isPersonal: Boolean,
        val dataPoints: Int,
        val daysSinceUpdate: Int
    )
}
