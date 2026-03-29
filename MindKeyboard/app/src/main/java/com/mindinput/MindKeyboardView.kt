package com.mindinput

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.mindinput.collector.TypingBehaviorCollector

/**
 * MindInput 键盘视图
 *
 * 职责：
 * 1. 渲染 QWERTY 键盘 UI
 * 2. 处理按键事件
 * 3. 与 InputMethodService 通信
 */
class MindKeyboardView(context: Context) : FrameLayout(context) {

    // 回调接口
    var onKeyEvent: ((keyCode: Int, text: String) -> Unit)? = null
    var onTextSubmitted: ((text: String) -> Unit)? = null
    var onDeleteKey: (() -> Unit)? = null

    // 行为收集器
    val typingCollector = TypingBehaviorCollector()

    // Shift 状态
    private var isShifted = false
    private var isCapsLock = false

    // 按键映射
    private val keyMap = mapOf(
        R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e",
        R.id.key_r to "r", R.id.key_t to "t", R.id.key_y to "y",
        R.id.key_u to "u", R.id.key_i to "i", R.id.key_o to "o",
        R.id.key_p to "p", R.id.key_a to "a", R.id.key_s to "s",
        R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g",
        R.id.key_h to "h", R.id.key_j to "j", R.id.key_k to "k",
        R.id.key_l to "l", R.id.key_z to "z", R.id.key_x to "x",
        R.id.key_c to "c", R.id.key_v to "v", R.id.key_b to "b",
        R.id.key_n to "n", R.id.key_m to "m"
    )

    init {
        // 加载键盘布局
        inflate(context, R.layout.keyboard_view, this)
        setupKeys()
    }

    private fun setupKeys() {
        // 字母键
        for ((id, char) in keyMap) {
            findViewById<Button>(id)?.setOnClickListener {
                handleCharacterKey(char)
            }
        }

        // 空格键
        findViewById<Button>(R.id.key_space)?.setOnClickListener {
            handleCharacterKey(" ")
        }

        // 删除键
        findViewById<Button>(R.id.key_delete)?.setOnClickListener {
            handleDeleteKey()
        }

        // Shift 键
        findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            toggleShift()
        }

        // 回车/完成键
        findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            handleEnterKey()
        }

        // 数字/符号键（简化：暂不实现）
        findViewById<Button>(R.id.key_123)?.setOnClickListener {
            // TODO: 切换到数字键盘
        }

        // 表情键（简化：暂不实现）
        findViewById<Button>(R.id.key_emoji)?.setOnClickListener {
            // TODO: 打开表情选择器
        }
    }

    private fun handleCharacterKey(char: String) {
        val text = if (isShifted && !isCapsLock) {
            char.uppercase()
        } else {
            char.lowercase()
        }

        // 记录按键事件
        typingCollector.recordKeyEvent(System.currentTimeMillis(), 0)

        // 回调
        onKeyEvent?.invoke(0, text)

        // Shift 单次按下后恢复小写（除非 CapsLock）
        if (isShifted && !isCapsLock) {
            isShifted = false
            updateShiftState()
        }
    }

    private fun handleDeleteKey() {
        // 记录退格
        typingCollector.recordKeyEvent(
            System.currentTimeMillis(),
            KeyEvent.KEYCODE_DEL
        )

        onDeleteKey?.invoke()
    }

    private fun handleEnterKey() {
        // 通知文本提交
        // 注意：实际文本提交由 InputMethodService 处理
        onTextSubmitted?.invoke("")
    }

    private fun toggleShift() {
        when {
            isCapsLock -> {
                // CapsLock 状态下单击 shift：恢复正常
                isCapsLock = false
                isShifted = false
            }
            isShifted -> {
                // Shift 状态下单击：进入 CapsLock
                isCapsLock = true
            }
            else -> {
                // 正常状态下单击：单次大写
                isShifted = true
            }
        }
        updateShiftState()
    }

    private fun updateShiftState() {
        val shiftButton = findViewById<Button>(R.id.key_shift)
        when {
            isCapsLock -> {
                shiftButton?.setBackgroundColor(context.getColor(R.color.purple_500))
                shiftButton?.setTextColor(context.getColor(R.color.white))
            }
            isShifted -> {
                shiftButton?.setBackgroundColor(context.getColor(R.color.teal_200))
                shiftButton?.text = "↑"
            }
            else -> {
                shiftButton?.setBackgroundResource(R.drawable.key_special_background)
                shiftButton?.setTextColor(context.getColor(R.color.key_special_text))
                shiftButton?.text = "↑"
            }
        }
    }

    /**
     * 获取 L1Container 用于添加提醒条
     */
    fun getL1Container(): LinearLayout? = findViewById(R.id.l1Container)

    /**
     * 获取键盘主体
     */
    fun getKeyboardBody(): LinearLayout? = findViewById(R.id.keyboardBody)

    /**
     * 重置打字会话
     */
    fun resetSession() {
        typingCollector.reset()
    }
}
