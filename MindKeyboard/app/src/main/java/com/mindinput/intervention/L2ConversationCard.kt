package com.mindinput.intervention

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.TextView
import com.mindinput.R

/**
 * L2 级干预：对话卡片
 * 当检测到用户情绪低落时，显示温和的对话卡片，询问是否需要帮助
 */
class L2ConversationCard(private val context: Context) {

    private var cardView: View? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    // 显示延迟（毫秒）- 给用户时间继续输入
    private var showDelayMs = 2000L
    private var autoDismissMs = 10_000L  // 10秒自动消失

    // 回调
    var onChat: (() -> Unit)? = null
    var onBreath: (() -> Unit)? = null
    var onDismissed: (() -> Unit)? = null

    // 私有 Runnable
    private var showRunnable: Runnable? = null
    private var dismissRunnable: Runnable? = null

    /**
     * 延迟显示卡片
     * @param sessionMinutes 当前会话时长（分钟）
     */
    fun showDelayed(sessionMinutes: Int) {
        if (isShowing) return

        showRunnable = Runnable {
            show()
        }
        handler.postDelayed(showRunnable!!, showDelayMs)
    }

    /**
     * 立即显示卡片
     */
    fun show() {
        if (isShowing) return

        // 取消延迟显示
        showRunnable?.let { handler.removeCallbacks(it) }

        // 移除旧视图
        cardView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        // 膨胀布局
        cardView = LayoutInflater.from(context)
            .inflate(R.layout.l2_conversation_card, null)

        // 设置消息
        cardView?.findViewById<TextView>(R.id.tvTitle)?.text =
            context.getString(R.string.l2_title)
        cardView?.findViewById<TextView>(R.id.tvMessage)?.text =
            context.getString(R.string.l2_message)

        // 设置按钮
        cardView?.findViewById<Button>(R.id.btnChat)?.setOnClickListener {
            hide()
            onChat?.invoke()
        }

        cardView?.findViewById<Button>(R.id.btnBreath)?.setOnClickListener {
            hide()
            onBreath?.invoke()
        }

        cardView?.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
            hide()
            onDismissed?.invoke()
        }

        // 创建悬浮卡片
        createFloatingCard()
    }

    private fun createFloatingCard() {
        // 创建一个悬浮的对话框
        val dialog = android.app.AlertDialog.Builder(context, R.style.Theme_MindKeyboard)
            .setView(cardView)
            .setCancelable(true)
            .create()

        // 设置动画
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)

        dialog.show()

        cardView?.let { view ->
            view.alpha = 0f
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
            }
            view.startAnimation(fadeIn)
        }

        isShowing = true

        // 设置自动消失
        dismissRunnable = Runnable {
            dialog.dismiss()
            hide()
        }
        handler.postDelayed(dismissRunnable!!, autoDismissMs)

        // Dialog 的 dismiss 监听
        dialog.setOnDismissListener {
            hide()
            handler.removeCallbacks(dismissRunnable!!)
        }
    }

    /**
     * 隐藏卡片
     */
    fun hide() {
        if (!isShowing) return

        showRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable?.let { handler.removeCallbacks(it) }

        isShowing = false
        cardView = null
    }

    /**
     * 设置显示延迟
     */
    fun setShowDelay(delayMs: Long) {
        showDelayMs = delayMs
    }

    /**
     * 设置自动消失时间
     */
    fun setAutoDismissTime(timeMs: Long) {
        autoDismissMs = timeMs
    }
}
