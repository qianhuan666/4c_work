package com.mindinput.analyzer

import com.mindinput.model.DistortionType
import com.mindinput.model.TextFeatures

/**
 * 危机词检测器
 * 在本地实时检测高危文本，不上报云端
 * 检测到危机时直接触发本地干预
 */
class CrisisDetector {

    companion object {
        // 危机词列表（必须本地检测，不过云）
        private val CRISIS_KEYWORDS = listOf(
            // 自我伤害相关
            "不想活", "不想活了", "活不下去", "活着没意思",
            "活着有什么意义", "死了算了", "想死", "不想活了",

            // 消失/逃离相关
            "消失", "消失掉", "想消失", "不想存在",

            // 无意义感
            "没意思", "什么都没意思", "一切都没意义",
            "活着没意义", "没有活下去的理由",

            // 绝望表达
            "绝望", "彻底绝望", "没希望了",
            "一切都完了", "彻底完了",

            // 自我否定极端
            "不配活着", "不如死了", "该死", "我该死"
        )

        // 认知扭曲关键词映射
        private val DISTORTION_KEYWORDS = mapOf(
            DistortionType.HELPLESSNESS to listOf(
                "算了", "放弃", "没用", "不行了", "废了",
                "无力", "无助", "无望", "改变不了"
            ),
            DistortionType.CATASTROPHIZING to listOf(
                "完了", "毁了", "太糟了", "怎么办", "怎么得了",
                "最坏的", "彻底完了", "无法挽回"
            ),
            DistortionType.OVERGENERALIZATION to listOf(
                "永远", "总是", "从来不", "从来没有",
                "所有人", "没人", "没有一个人", "一直"
            ),
            DistortionType.SELF_BLAME to listOf(
                "都是我的错", "我太差了", "我不行", "是我的错",
                "怪我", "对不起", "我不配", "我不值得"
            ),
            DistortionType.MIND_READING to listOf(
                "他们肯定觉得", "别人肯定觉得我", "大家都在想"
            ),
            DistortionType.FORTUNE_TELLING to listOf(
                "肯定不行", "估计又要失败", "一定会搞砸",
                "不会有好结果的", "注定要失败"
            )
        )

        // 积极应对信号（降低风险）
        private val POSITIVE_COPING_KEYWORDS = listOf(
            "加油", "努力", "可以的", "慢慢来", "没关系",
            "会好的", "一切都会过去", "想办法", "解决",
            "找人聊聊", "运动一下", "休息一下", "会好起来的"
        )
    }

    /**
     * 检测危机信号
     * @param text 用户输入文本
     * @return true 如果检测到危机信号
     */
    fun detectCrisis(text: String): Boolean {
        val normalized = text.lowercase()
        return CRISIS_KEYWORDS.any { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    /**
     * 检测认知扭曲类型
     * @param text 用户输入文本
     * @return 命中的认知扭曲类型列表
     */
    fun detectDistortions(text: String): List<DistortionType> {
        val detected = mutableListOf<DistortionType>()
        val normalized = text.lowercase()

        for ((type, keywords) in DISTORTION_KEYWORDS) {
            if (keywords.any { keyword -> normalized.contains(keyword.lowercase()) }) {
                detected.add(type)
            }
        }

        return detected
    }

    /**
     * 统计积极应对信号数量
     * @param text 用户输入文本
     * @return 积极词汇命中数量
     */
    fun countPositiveSignals(text: String): Int {
        val normalized = text.lowercase()
        return POSITIVE_COPING_KEYWORDS.count { keyword ->
            normalized.contains(keyword.lowercase())
        }
    }

    /**
     * 计算一人称代词密度（抑郁指标）
     * @param text 用户输入文本
     * @return "我"字占比
     */
    fun calculateFirstPersonDensity(text: String): Float {
        if (text.isEmpty()) return 0f
        val firstPersonCount = text.count { it == '我' }
        return firstPersonCount.toFloat() / text.length.toFloat()
    }

    /**
     * 完整分析文本
     * @param text 用户输入文本
     * @return TextFeatures 特征对象
     */
    fun analyze(text: String): TextFeatures {
        if (text.isEmpty()) {
            return TextFeatures()
        }

        val crisis = detectCrisis(text)
        val distortions = detectDistortions(text)
        val distortionFlags = DistortionType.toFlags(distortions)
        val positiveSignals = countPositiveSignals(text)
        val firstPersonDensity = calculateFirstPersonDensity(text)

        return TextFeatures(
            sentimentScore = calculateSentiment(text),
            sentimentCategory = categorizeSentiment(text),
            distortionFlags = distortionFlags,
            firstPersonDensity = firstPersonDensity,
            positiveSignalCount = positiveSignals,
            isCrisis = crisis,
            distortionTypes = distortions,
            textLength = text.length,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 简单情感计算（基于关键词）
     * @return 0.0（非常负面）到 1.0（非常正面）
     */
    private fun calculateSentiment(text: String): Float {
        val normalized = text.lowercase()
        var score = 0.5f // 默认中性

        // 负面词减分
        val negativeWords = listOf(
            "难过", "伤心", "痛苦", "焦虑", "害怕", "担心",
            "压力", "累", "崩溃", "绝望", "失落", "沮丧",
            "郁闷", "烦躁", "生气", "愤怒", "讨厌", "恨"
        )

        // 正面词加分
        val positiveWords = listOf(
            "开心", "快乐", "高兴", "愉快", "舒服", "轻松",
            "幸福", "满足", "感恩", "希望", "期待", "兴奋",
            "激动", "满意", "安心", "平静", "舒服"
        )

        for (word in negativeWords) {
            if (normalized.contains(word)) score -= 0.1f
        }

        for (word in positiveWords) {
            if (normalized.contains(word)) score += 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * 情感分类
     * @return -1 负面, 0 中性, 1 正面
     */
    private fun categorizeSentiment(text: String): Int {
        val score = calculateSentiment(text)
        return when {
            score < 0.4f -> -1
            score > 0.6f -> 1
            else -> 0
        }
    }
}
