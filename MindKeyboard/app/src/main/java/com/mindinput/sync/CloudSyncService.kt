package com.mindinput.sync

import android.content.Context
import com.mindinput.model.UploadPayload
import com.mindinput.detector.BaselineManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 云端匿名同步服务
 *
 * 职责：
 * 1. 将匿名特征数据上报到云端
 * 2. 获取群体基线用于新用户
 * 3. 跨设备同步个人基线
 *
 * 注意：绝不传输任何原始文本内容
 */
class CloudSyncService(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val baselineManager = BaselineManager(context)
    private val gson = Gson()

    // 上报队列
    private val pendingPayloads = mutableListOf<UploadPayload>()
    private val maxQueueSize = 100

    // HTTP 客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        // TODO: 替换为实际的 API 地址
        private const val BASE_URL = "https://api.mindinput.example.com"
        private const val ENDPOINT_UPLOAD = "/v1/analytics/upload"
        private const val ENDPOINT_BASELINE = "/v1/baseline/group"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * 队列上报数据
     * 注意：立即返回，数据在后台发送
     */
    fun queueUpload(payload: UploadPayload) {
        synchronized(pendingPayloads) {
            pendingPayloads.add(payload)
            if (pendingPayloads.size >= maxQueueSize) {
                flushQueue()
            }
        }
    }

    /**
     * 立即发送数据
     */
    fun sendNow(payload: UploadPayload) {
        scope.launch {
            uploadPayload(payload)
        }
    }

    /**
     * 刷新队列
     */
    fun flushQueue() {
        scope.launch {
            val payloads = synchronized(pendingPayloads) {
                val list = pendingPayloads.toList()
                pendingPayloads.clear()
                list
            }

            for (payload in payloads) {
                uploadPayload(payload)
            }
        }
    }

    /**
     * 执行实际上报
     */
    private suspend fun uploadPayload(payload: UploadPayload): Result<Unit> {
        return try {
            val json = gson.toJson(payload)
            val body = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$BASE_URL$ENDPOINT_UPLOAD")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Upload failed: ${response.code}"))
            }
        } catch (e: Exception) {
            // 网络错误，标记稍后重试
            synchronized(pendingPayloads) {
                if (pendingPayloads.size < maxQueueSize * 2) {
                    pendingPayloads.add(payload)
                }
            }
            Result.failure(e)
        }
    }

    /**
     * 获取群体基线（用于新用户）
     */
    suspend fun fetchGroupBaseline(): Result<GroupBaselineResponse>? {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL$ENDPOINT_BASELINE")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = response.body?.string()
                val baseline = gson.fromJson(json, GroupBaselineResponse::class.java)
                Result.success(baseline)
            } else {
                Result.failure(Exception("Fetch failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 生成匿名 ID（SHA-256）
     */
    fun generateAnonymousId(deviceId: String, salt: String): String {
        val input = "$deviceId:$salt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 创建上报载荷
     */
    fun createPayload(
        typingFeatures: com.mindinput.model.TypingFeatures,
        textFeatures: com.mindinput.model.TextFeatures,
        anxietyLevel: Int,
        riskScore: Float,
        consecutiveAlertDays: Int
    ): UploadPayload {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()

        return UploadPayload(
            anonymousId = baselineManager.getUserId(),
            hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK),
            typingSpeedZ = typingFeatures.typingSpeed / 40f,  // 简化计算
            backspaceZ = typingFeatures.backspaceRate / 0.05f,
            sentimentScore = textFeatures.sentimentScore,
            distortionFlags = textFeatures.distortionFlags,
            anxietyLevel = anxietyLevel,
            consecutiveAlertDays = consecutiveAlertDays,
            riskScore = riskScore,
            timestamp = now
        )
    }

    data class GroupBaselineResponse(
        val avgTypingSpeed: Float,
        val stdTypingSpeed: Float,
        val avgBackspaceRate: Float,
        val stdBackspaceRate: Float,
        val avgSentiment: Float,
        val stdSentiment: Float,
        val version: Int
    )
}
