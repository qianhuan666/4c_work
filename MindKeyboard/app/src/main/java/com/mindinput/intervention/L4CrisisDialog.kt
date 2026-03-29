package com.mindinput.intervention

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mindinput.R

/**
 * L4 级干预：危机干预弹窗
 * 当检测到用户可能有自我伤害风险时，显示不可关闭的危机干预弹窗
 */
class L4CrisisDialog(private val context: Context) {

    companion object {
        // 北京心理危机研究与干预中心热线
        const val CRISIS_HOTLINE = "400-161-9995"
        // 全国心理援助热线
        const val NATIONAL_HOTLINE = "400-821-1215"
    }

    private var dialog: AlertDialog? = null

    /**
     * 显示危机干预弹窗
     * 注意：此弹窗不可关闭，用户必须主动选择才能消失
     */
    fun show() {
        if (dialog?.isShowing == true) return

        val builder = AlertDialog.Builder(context).apply {
            setCancelable(false)  // 强制用户选择，不能直接关闭

            setTitle(R.string.l4_title)
            setMessage(R.string.l4_message)

            setPositiveButton(R.string.l4_btn_talk) { _, _ ->
                // 打开拨号界面
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$CRISIS_HOTLINE")
                }
                context.startActivity(intent)
            }

            setNegativeButton(R.string.l4_btn_ok) { _, _ ->
                // 用户表示没事，但仍建议关注
            }
        }

        dialog = builder.create()
        dialog?.show()

        // 设置按钮样式
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(context.getColor(R.color.crisis_hotline))
        }
    }

    /**
     * 关闭弹窗
     */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /**
     * 检查弹窗是否正在显示
     */
    fun isShowing(): Boolean = dialog?.isShowing == true
}

/**
 * 简单的深呼吸引导（可嵌入 L2 对话卡片）
 */
class BreathingGuide(private val context: Context) {

    private var isActive = false
    private var currentPhase = 0  // 0=吸气, 1=屏息, 2=呼气
    private var cycleCount = 0

    companion object {
        private const val INHALE_DURATION = 4000L   // 4秒吸气
        private const val HOLD_DURATION = 4000L     // 4秒屏息
        private const val EXHALE_DURATION = 6000L   // 6秒呼气
        private const val TOTAL_CYCLES = 3         // 3个完整周期
    }

    var onPhaseChange: ((String, Int) -> Unit)? = null  // (描述, 剩余秒数)
    var onComplete: (() -> Unit)? = null

    private var handler: android.os.Handler? = null

    fun start() {
        isActive = true
        cycleCount = 0
        currentPhase = 0
        handler = android.os.Handler(android.os.Looper.getMainLooper())

        runPhase()
    }

    fun stop() {
        isActive = false
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    private fun runPhase() {
        if (!isActive) return

        when (currentPhase) {
            0 -> {
                onPhaseChange?.invoke("吸气...", 4)
                handler?.postDelayed({
                    currentPhase = 1
                    runPhase()
                }, INHALE_DURATION)
            }
            1 -> {
                onPhaseChange?.invoke("屏息...", 4)
                handler?.postDelayed({
                    currentPhase = 2
                    runPhase()
                }, HOLD_DURATION)
            }
            2 -> {
                onPhaseChange?.invoke("呼气...", 6)
                handler?.postDelayed({
                    cycleCount++
                    if (cycleCount < TOTAL_CYCLES) {
                        currentPhase = 0
                        runPhase()
                    } else {
                        isActive = false
                        onComplete?.invoke()
                    }
                }, EXHALE_DURATION)
            }
        }
    }
}
