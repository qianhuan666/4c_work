package com.mindinput.model

/**
 * 文本分析特征数据类
 * 从用户输入文本中提取的情感/语言学特征
 * 注意：原始文本永不存储，此处仅为特征值
 */
data class TextFeatures(
    /** 情感得分（0.0 = 非常负面，1.0 = 非常正面） */
    val sentimentScore: Float = 0.5f,

    /** 情感分类（-1=负面, 0=中性, 1=正面） */
    val sentimentCategory: Int = 0,

    /** 认知扭曲类型标志（位标志） */
    val distortionFlags: Int = 0,

    /** 一人称代词密度（"我"字占比） */
    val firstPersonDensity: Float = 0f,

    /** 积极应对信号计数 */
    val positiveSignalCount: Int = 0,

    /** 是否检测到危机信号 */
    val isCrisis: Boolean = false,

    /** 检测到的认知扭曲类型列表 */
    val distortionTypes: List<DistortionType> = emptyList(),

    /** 文本长度（用于归一化） */
    val textLength: Int = 0,

    /** 分析时间戳 */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty() = TextFeatures()
    }
}

/**
 * 认知扭曲类型枚举
 */
enum class DistortionType(val bit: Int) {
    HELPLESSNESS(0),        // 绝望感
    CATASTROPHIZING(1),     // 灾难化
    OVERGENERALIZATION(2),  // 绝对化
    SELF_BLAME(3),          // 自我否定
    MIND_READING(4),        // 读心术
    FORTUNE_TELLING(5);     // 预言

    companion object {
        fun fromFlags(flags: Int): List<DistortionType> {
            return entries.filter { (flags shr it.bit) and 1 == 1 }
        }

        fun toFlags(types: List<DistortionType>): Int {
            return types.fold(0) { acc, type -> acc or (1 shl type.bit) }
        }
    }
}
