package com.mindinput.intervention

import android.content.Context
import android.view.ViewGroup
import com.mindinput.model.AnxietyLevel
import com.mindinput.model.MentalState

/**
 * 干预引擎
 * 根据 MentalState 决定触发哪个级别的干预
 */
class InterventionEngine(private val context: Context) {

    // 干预组件
    private val l1Reminder = L1RestReminder(context)
    private val l2Card = L2ConversationCard(context)
    private val l4Crisis = L4CrisisDialog(context)

    // 状态
    private var lastTriggeredLevel = AnxietyLevel.L0_NORMAL
    private var lastTriggerTime = 0L
    private val triggerCooldownMs = 5 * 60 * 1000L  // 5分钟冷却

    // 回调
    var onL3Triggered: ((MentalState) -> Unit)? = null  // L3 需要外部处理（如通知辅导员）

    // L1 回调设置
    fun setL1Callbacks(
        onDismissed: () -> Unit = {},
        onAccepted: () -> Unit = {}
    ) {
        l1Reminder.onDismissed = onDismissed
        l1Reminder.onAccepted = onAccepted
    }

    // L2 回调设置
    fun setL2Callbacks(
        onChat: () -> Unit = {},
        onBreath: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        l2Card.onChat = onChat
        l2Card.onBreath = onBreath
        l2Card.onDismissed = onDismissed
    }

    /**
     * 处理心理状态评估结果，决定是否触发干预
     * @param state 评估结果
     * @param sessionMinutes 当前会话时长
     * @param keyboardContainer 键盘视图容器（用于添加 L1/L2 UI）
     */
    fun processState(
        state: MentalState,
        sessionMinutes: Int,
        keyboardContainer: ViewGroup?
    ) {
        // 检查冷却期
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < triggerCooldownMs && state.level.value <= lastTriggeredLevel.value) {
            return
        }

        when (state.level) {
            AnxietyLevel.L4_CRISIS -> {
                // 危机：立即显示，不可延迟
                l4Crisis.show()
                lastTriggeredLevel = state.level
                lastTriggerTime = now
            }

            AnxietyLevel.L3_HIGH -> {
                // 高风险：触发 L3（需要用户授权通知辅导员）
                onL3Triggered?.invoke(state)
                lastTriggeredLevel = state.level
                lastTriggerTime = now
            }

            AnxietyLevel.L2_MEDIUM -> {
                // 中风险：L2 对话卡片
                keyboardContainer?.let {
                    l2Card.showDelayed(sessionMinutes)
                }
                lastTriggeredLevel = state.level
                lastTriggerTime = now
            }

            AnxietyLevel.L1_LOW -> {
                // 低风险：L1 休息提醒
                if (l1Reminder.shouldShow(sessionMinutes)) {
                    keyboardContainer?.let {
                        l1Reminder.show(it, sessionMinutes)
                    }
                }
                lastTriggeredLevel = state.level
                lastTriggerTime = now
            }

            AnxietyLevel.L0_NORMAL -> {
                // 正常：无干预
                lastTriggeredLevel = state.level
                // 不更新 lastTriggerTime，允许立即触发
            }
        }
    }

    /**
     * 隐藏所有干预 UI
     */
    fun hideAll() {
        l1Reminder.hide()
        l2Card.hide()
        l4Crisis.dismiss()
    }

    /**
     * 释放资源
     */
    fun release() {
        hideAll()
    }
}
