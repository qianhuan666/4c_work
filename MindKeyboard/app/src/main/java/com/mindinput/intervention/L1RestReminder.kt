package com.mindinput.intervention

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import com.mindinput.R

/**
 * L1 级干预：休息提醒
 * 当用户连续打字超过阈值时，在键盘顶部淡入显示休息提示
 */
class L1RestReminder(private val context: Context) {

    private var reminderView: View? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())

    // 显示条件
    private var minSessionMinutes = 30  // 最少连续打字 30 分钟
    private var dismissTimeoutMs = 60_000L  // 60 秒后自动消失

    // 回调
    var onDismissed: (() -> Unit)? = null
    var onAccepted: (() -> Unit)? = null

    /**
     * 检查是否应该显示提醒
     */
    fun shouldShow(sessionMinutes: Int): Boolean {
        return sessionMinutes >= minSessionMinutes && !isShowing
    }

    /**
     * 显示休息提醒
     */
    fun show(parent: ViewGroup, sessionMinutes: Int) {
        if (isShowing) return

        // 移除旧视图
        reminderView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        // 膨胀布局
        reminderView = LayoutInflater.from(context)
            .inflate(R.layout.l1_reminder_bar, parent, false)

        // 设置消息
        reminderView?.findViewById<TextView>(R.id.tvMessage)?.text =
            context.getString(R.string.l1_reminder_message, sessionMinutes)

        // 设置按钮
        reminderView?.findViewById<Button>(R.id.btnOk)?.setOnClickListener {
            hide()
            onAccepted?.invoke()
        }

        reminderView?.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
            hide()
            onDismissed?.invoke()
        }

        // 添加到父视图
        parent.addView(reminderView)

        // 淡入动画
        reminderView?.alpha = 0f
        reminderView?.visibility = View.VISIBLE
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 500
            fillAfter = true
        }
        reminderView?.startAnimation(fadeIn)

        isShowing = true

        // 设置自动消失
        handler.postDelayed({
            hide()
        }, dismissTimeoutMs)
    }

    /**
     * 隐藏提醒
     */
    fun hide() {
        if (!isShowing) return

        reminderView?.let {
            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 300
                fillAfter = true
            }
            it.startAnimation(fadeOut)

            handler.postDelayed({
                (it.parent as? ViewGroup)?.removeView(it)
            }, 300)
        }

        isShowing = false
        reminderView = null
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 设置最少显示的会话时长（分钟）
     */
    fun setMinSessionMinutes(minutes: Int) {
        minSessionMinutes = minutes
    }

    /**
     * 设置自动消失超时（毫秒）
     */
    fun setDismissTimeout(timeoutMs: Long) {
        dismissTimeoutMs = timeoutMs
    }
}
