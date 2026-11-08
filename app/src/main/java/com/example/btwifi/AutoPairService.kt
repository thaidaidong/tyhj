package com.example.btwifi

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Tự bấm OK trên hộp thoại ghép nối Bluetooth, chỉ trong thời gian ngắn sau khi ghép từ web. */
class AutoPairService : AccessibilityService() {
    override fun onServiceConnected() { connected = true }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (System.currentTimeMillis() > allowUntil) return
        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collect(root, texts)
        val all = texts.joinToString(" | ")
        val isPairDialog = Regex("(?i)(ghép đôi với|pair with|yêu cầu kết nối bluetooth|bluetooth pairing request)")
            .containsMatchIn(all)
        if (!isPairDialog) return
        if (expectName.isNotEmpty() && !all.contains(expectName, ignoreCase = true)) return
        for (label in LABELS) {
            val node = root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.text?.toString()?.trim().equals(label, ignoreCase = true) }
            if (node != null && clickUp(node)) return
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        n.text?.let { out.add(it.toString()) }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun clickUp(n: AccessibilityNodeInfo?): Boolean {
        var c = n
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            c = c.parent
        }
        return false
    }

    companion object {
        @Volatile var connected = false
        @Volatile var allowUntil = 0L
        @Volatile var expectName = ""
        private val LABELS = listOf("OK", "Ghép nối", "Ghép đôi", "Pair", "Chấp nhận", "Accept", "Đồng ý")
        fun arm(name: String) { expectName = name; allowUntil = System.currentTimeMillis() + 60_000 }
        fun disarm() { allowUntil = 0L }
    }
}
