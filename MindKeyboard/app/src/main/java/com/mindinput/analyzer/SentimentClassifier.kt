package com.mindinput.analyzer

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.mindinput.model.TextFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer

/**
 * 基于 ONNX Runtime 的端侧情感分类器
 *
 * 注意：这是简化版本，实际使用时需要：
 * 1. 替换 assets/sentiment_model.onnx 为真实训练好的模型
 * 2. 实现完整的 tokenization 逻辑
 * 3. 根据实际模型输入输出调整
 */
class SentimentClassifier(private val context: Context) {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    companion object {
        private const val MODEL_PATH = "sentiment_model.onnx"
        private const val MAX_INPUT_LENGTH = 128
    }

    /**
     * 初始化 ONNX 模型
     * 在后台线程调用
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
            ortSession = ortEnvironment?.createSession(modelBytes)
            isModelLoaded = true
        } catch (e: Exception) {
            // 模型加载失败，使用基于关键词的 fallback
            isModelLoaded = false
        }
    }

    /**
     * 使用 ONNX 模型进行情感分类
     * @param text 输入文本
     * @return 情感得分（0.0-1.0，0.5为中性）
     */
    suspend fun classify(text: String): Float = withContext(Dispatchers.IO) {
        if (!isModelLoaded || text.isEmpty()) {
            return@withContext 0.5f
        }

        try {
            // 简化的 tokenization（实际应使用 proper tokenizer）
            val inputIds = tokenize(text)

            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                LongBuffer.wrap(inputIds),
                longArrayOf(1, inputIds.size.toLong())
            )

            val outputs = ortSession?.run(mapOf("input_ids" to inputTensor))
            val result = outputs?.get(0)?.value as? Array<FloatArray>

            inputTensor.close()

            // 返回负面情感得分（0=正面，1=负面，取反）
            result?.get(0)?.get(0)?.let { 1f - it } ?: 0.5f

        } catch (e: Exception) {
            // ONNX 推理失败，返回默认值
            0.5f
        }
    }

    /**
     * 释放 ONNX 资源
     */
    fun release() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
        } catch (e: Exception) {
            // 忽略关闭异常
        }
        isModelLoaded = false
    }

    /**
     * 简化的 tokenization（需替换为 proper tokenizer）
     */
    private fun tokenize(text: String): LongArray {
        // 简化的字符级 tokenization（实际应使用 BERT/W2V tokenizer）
        val chars = text.take(MAX_INPUT_LENGTH - 2).toCharArray()
        val tokens = LongArray(chars.size + 2)

        // [CLS] token
        tokens[0] = 101

        // 字符 tokens
        for (i in chars.indices) {
            tokens[i + 1] = (chars[i].code % 1000).toLong() + 1000
        }

        // [SEP] token
        tokens[tokens.size - 1] = 102

        return tokens
    }
}

/**
 * ONNX 模型不可用时的 fallback 情感分析器
 * 基于关键词规则
 */
class RuleBasedSentimentAnalyzer {

    private val positiveWords = setOf(
        "开心", "快乐", "高兴", "愉快", "幸福", "满足",
        "感恩", "希望", "期待", "兴奋", "激动", "棒",
        "加油", "赞", "好", "不错", "喜欢", "爱"
    )

    private val negativeWords = setOf(
        "难过", "伤心", "痛苦", "焦虑", "害怕", "担心",
        "压力", "累", "崩溃", "绝望", "失落", "沮丧",
        "郁闷", "烦躁", "生气", "愤怒", "讨厌", "恨",
        "糟", "坏", "差", "烂", "悲", "惨"
    )

    fun analyze(text: String): Float {
        if (text.isEmpty()) return 0.5f

        val words = text.lowercase()
        var score = 0.5f

        var positiveCount = 0
        var negativeCount = 0

        for (word in positiveWords) {
            if (words.contains(word)) positiveCount++
        }

        for (word in negativeWords) {
            if (words.contains(word)) negativeCount++
        }

        val total = positiveCount + negativeCount
        if (total > 0) {
            score = negativeCount.toFloat() / total.toFloat()
        }

        return score.coerceIn(0f, 1f)
    }
}
