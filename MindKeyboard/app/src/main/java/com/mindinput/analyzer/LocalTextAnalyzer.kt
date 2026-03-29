package com.mindinput.analyzer

import android.content.Context
import com.mindinput.model.TextFeatures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 本地文本分析器
 *
 * 职责：
 * 1. 危机词实时检测（本地拦截，不过云）
 * 2. 认知扭曲关键词检测
 * 3. 语言学特征提取
 * 4. 情感分类（ONNX 或基于规则）
 *
 * 设计原则：原文永不离开设备，只提取特征值
 */
class LocalTextAnalyzer(context: Context) {

    private val crisisDetector = CrisisDetector()
    private val sentimentAnalyzer = RuleBasedSentimentAnalyzer()
    private val sentimentClassifier = SentimentClassifier(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 回调接口
    var onCrisisDetected: ((String) -> Unit)? = null
    var onAnalysisComplete: ((TextFeatures) -> Unit)? = null

    init {
        // 异步初始化 ONNX 模型
        scope.launch {
            sentimentClassifier.initialize()
        }
    }

    /**
     * 分析文本（异步）
     * @param text 用户输入文本
     */
    fun analyzeAsync(text: String) {
        scope.launch {
            val features = analyze(text)

            // 如果检测到危机，通过回调通知
            if (features.isCrisis) {
                onCrisisDetected?.invoke(text)
            }

            onAnalysisComplete?.invoke(features)
        }
    }

    /**
     * 分析文本（同步）
     * @param text 用户输入文本
     * @return TextFeatures 特征对象
     */
    fun analyze(text: String): TextFeatures {
        if (text.isEmpty()) {
            return TextFeatures()
        }

        // 第一步：危机检测（最高优先级）
        val isCrisis = crisisDetector.detectCrisis(text)

        // 如果是危机，立即返回，不过云
        if (isCrisis) {
            return TextFeatures(
                isCrisis = true,
                sentimentScore = 0f,
                sentimentCategory = -1,
                textLength = text.length,
                timestamp = System.currentTimeMillis()
            )
        }

        // 第二步：认知扭曲检测
        val distortions = crisisDetector.detectDistortions(text)
        val distortionFlags = com.mindinput.model.DistortionType.toFlags(distortions)

        // 第三步：语言学特征
        val firstPersonDensity = crisisDetector.calculateFirstPersonDensity(text)
        val positiveSignals = crisisDetector.countPositiveSignals(text)

        // 第四步：情感分类
        val sentimentScore = sentimentAnalyzer.analyze(text)
        val sentimentCategory = when {
            sentimentScore < 0.4f -> -1
            sentimentScore > 0.6f -> 1
            else -> 0
        }

        return TextFeatures(
            sentimentScore = sentimentScore,
            sentimentCategory = sentimentCategory,
            distortionFlags = distortionFlags,
            firstPersonDensity = firstPersonDensity,
            positiveSignalCount = positiveSignals,
            isCrisis = false,
            distortionTypes = distortions,
            textLength = text.length,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 快速危机检测（同步，用于输入过程中实时检测）
     * @param text 当前输入文本
     * @return true 如果检测到危机信号
     */
    fun quickCrisisCheck(text: String): Boolean {
        return crisisDetector.detectCrisis(text)
    }

    /**
     * 释放资源
     */
    fun release() {
        sentimentClassifier.release()
    }
}
