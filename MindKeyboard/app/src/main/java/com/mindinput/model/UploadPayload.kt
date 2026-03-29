package com.mindinput.model

/**
 * 上报到云端的匿名数据载荷
 * 注意：绝不含任何原始文本内容
 */
data class UploadPayload(
    /** 匿名化用户ID（SHA-256哈希，非真实账号） */
    val anonymousId: String,

    /** 时间（精确到小时） */
    val hourOfDay: Int,

    /** 星期几（1-7） */
    val dayOfWeek: Int,

    /** 打字速度Z分数 */
    val typingSpeedZ: Float,

    /** 退格率Z分数 */
    val backspaceZ: Float,

    /** 情感得分（0-1） */
    val sentimentScore: Float,

    /** 认知扭曲标志位 */
    val distortionFlags: Int,

    /** 焦虑等级（0-4） */
    val anxietyLevel: Int,

    /** 连续异常天数 */
    val consecutiveAlertDays: Int,

    /** 风险评分 */
    val riskScore: Float,

    /** 上报时间戳 */
    val timestamp: Long = System.currentTimeMillis()
)
