package com.mindinput.collector

import android.text.Editable
import android.text.TextWatcher

/**
 * 文本提交监听器
 * 用于监听用户提交文本（如发送消息）的事件
 * 可以获取发送前的编辑时长等信息
 */
class TextSubmitListener(
    private val onTextSubmitted: (String) -> Unit
) : TextWatcher {

    private var textBeforeChange: CharSequence? = null
    private var firstEditTime: Long = 0L
    private var hasInteracted = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (!hasInteracted) {
            firstEditTime = System.currentTimeMillis()
            hasInteracted = true
        }
        textBeforeChange = s?.toString()
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        // 不需要处理
    }

    override fun afterTextChanged(s: Editable?) {
        // 不需要处理
    }

    /**
     * 当用户提交文本时调用
     * @param submittedText 提交的完整文本
     */
    fun notifySubmit(submittedText: String) {
        if (submittedText.isNotEmpty()) {
            onTextSubmitted(submittedText)
        }
        reset()
    }

    /**
     * 获取编辑时长（毫秒）
     */
    fun getEditingDuration(): Long {
        return if (hasInteracted && firstEditTime > 0) {
            System.currentTimeMillis() - firstEditTime
        } else 0L
    }

    /**
     * 重置状态
     */
    fun reset() {
        textBeforeChange = null
        firstEditTime = 0L
        hasInteracted = false
    }
}
